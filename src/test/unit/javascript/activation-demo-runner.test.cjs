const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createRunner } = require('../../../main/resources/swagger/activation-demo-runner.js');

const body = { externalId: 'sample', customerId: 'CUSTOMER-001', productCode: 'INTERNET-500' };
const settings = {
    path: '/api/v1/requests', pollIntervalMs: 1, timeoutMs: 1000, requestTimeoutMs: 100,
    maxBodyBytes: 16384, rateLimitMaxRequests: 2, rateLimitWindowMs: 100,
    scenarios: { sucesso: { label: 'Sucesso', kind: 'create', body, expectedHttp: 201, expectedStatus: 'COMPLETED' } }
};

function harness(responses, overrides = {}) {
    const requests = [];
    const delays = [];
    let nextId = 0;
    const runner = createRunner({
        settings: { ...settings, ...overrides },
        uuid: () => `unique-${++nextId}`,
        wait: async (milliseconds) => { delays.push(milliseconds); },
        transport: async (url, options) => {
            requests.push({ url, ...options });
            assert.ok(responses.length, 'No unexpected HTTP requests');
            return responses.shift();
        }
    });
    return { runner, requests, delays };
}

function response(status, value, headers = {}) {
    return { status, body: value, headers };
}

test('a selection creates a fresh request and follows its returned ID until completion', async () => {
    const context = harness([
        response(201, { id: 'request-one', status: 'PENDING' }),
        response(200, { id: 'request-one', status: 'PROCESSING' }),
        response(200, { id: 'request-one', status: 'COMPLETED' }),
        response(201, { id: 'request-two', status: 'PENDING' }),
        response(200, { id: 'request-two', status: 'COMPLETED' })
    ]);
    const first = await context.runner.run('sucesso');
    const second = await context.runner.run('sucesso');
    assert.equal(first.terminalStatus, 'COMPLETED');
    assert.equal(first.requestId, 'request-one');
    assert.equal(second.requestId, 'request-two');
    assert.equal(context.requests[1].url, '/api/v1/requests/request-one');
    assert.notEqual(JSON.parse(context.requests[0].body).externalId, JSON.parse(context.requests[3].body).externalId);
    assert.equal(body.externalId, 'sample');
});

test('invalid input is sent unchanged and its expected rejection completes without polling', async () => {
    const invalid = { ...body, externalId: '' };
    const context = harness([response(400, { code: 'validation-error' })], {
        scenarios: { invalid: { kind: 'invalid', body: invalid, expectedHttp: 400 } }
    });
    const result = await context.runner.run('invalid');
    assert.equal(result.steps.at(-1).status, 400);
    assert.deepEqual(JSON.parse(context.requests[0].body), invalid);
    assert.equal(context.requests.length, 1);
});

test('replay can be selected first and reuses exactly the automatically created payload', async () => {
    const context = harness([
        response(201, { id: 'replay-id', status: 'PENDING' }),
        response(200, { id: 'replay-id', status: 'COMPLETED' })
    ], { scenarios: {
        ...settings.scenarios, replay: { kind: 'replay', body, expectedHttp: 200 }
    } });
    const result = await context.runner.run('replay');
    assert.equal(result.requestId, 'replay-id');
    assert.equal(context.requests[1].method, 'POST');
    assert.equal(context.requests[0].body, context.requests[1].body);
});

test('conflict prepares its own request before submitting a different product', async () => {
    const context = harness([
        response(201, { id: 'original-id', status: 'PENDING' }),
        response(409, { code: 'duplicate-request' })
    ], { scenarios: {
        ...settings.scenarios,
        conflict: { kind: 'conflict', body: { ...body, productCode: 'INTERNET-1000' }, expectedHttp: 409 }
    } });
    const result = await context.runner.run('conflict');
    const original = JSON.parse(context.requests[0].body);
    const conflicting = JSON.parse(context.requests[1].body);
    assert.equal(result.steps.at(-1).status, 409);
    assert.equal(original.externalId, conflicting.externalId);
    assert.equal(original.productCode, 'INTERNET-500');
    assert.equal(conflicting.productCode, 'INTERNET-1000');
});

test('an unexpected HTTP error fails visibly without polling or retrying the creation', async () => {
    const context = harness([response(500, { detail: 'internal failure' })]);
    await assert.rejects(context.runner.run('sucesso'), /Esperado HTTP 201; recebido HTTP 500/);
    assert.equal(context.requests.length, 1);
});

test('a provider failure is successful only when its state and failure code match the scenario', async () => {
    const context = harness([
        response(201, { id: 'failure-id', status: 'PENDING' }),
        response(200, { id: 'failure-id', status: 'FAILED', failureCode: 'UNEXPECTED_FAILURE' })
    ], { scenarios: { failure: {
        kind: 'create', body, expectedHttp: 201, expectedStatus: 'FAILED', expectedFailureCode: 'PROVIDER_HTTP_400'
    } } });
    await assert.rejects(context.runner.run('failure'), /PROVIDER_HTTP_400/);
});

test('cancelling before execution prevents any HTTP request', async () => {
    const controller = new AbortController();
    controller.abort();
    const context = harness([]);
    await assert.rejects(context.runner.run('sucesso', { signal: controller.signal }), { name: 'AbortError' });
    assert.equal(context.requests.length, 0);
});

test('lookup creates its own fixture and queries the returned ID without user input', async () => {
    const context = harness([
        response(201, { id: 'lookup-id', status: 'PENDING' }),
        response(200, { id: 'lookup-id', status: 'COMPLETED' })
    ], { scenarios: { ...settings.scenarios, lookup: { kind: 'lookup', expectedHttp: 200 } } });
    const result = await context.runner.run('lookup');
    assert.equal(JSON.parse(context.requests[0].body).productCode, 'INTERNET-500');
    assert.equal(context.requests[1].url, '/api/v1/requests/lookup-id');
    assert.equal(result.requestId, 'lookup-id');
});

test('lookup still queries the resource when creation already returns a terminal state', async () => {
    const context = harness([
        response(201, { id: 'fast-id', status: 'COMPLETED' }),
        response(200, { id: 'fast-id', status: 'COMPLETED' })
    ], { scenarios: { ...settings.scenarios, lookup: { kind: 'lookup', expectedHttp: 200 } } });
    const result = await context.runner.run('lookup');
    assert.equal(context.requests.length, 2);
    assert.equal(context.requests[1].method, 'GET');
    assert.equal(context.requests[1].url, '/api/v1/requests/fast-id');
    assert.equal(result.steps.at(-1).status, 200);
});

test('missing lookup generates an ID and validates the real 404 response', async () => {
    const context = harness([response(404, { code: 'request-not-found' })], {
        scenarios: { missing: { kind: 'missing', expectedHttp: 404 } }
    });
    const result = await context.runner.run('missing');
    assert.equal(context.requests[0].method, 'GET');
    assert.equal(context.requests[0].url, '/api/v1/requests/unique-1');
    assert.equal(result.steps.at(-1).status, 404);
});

test('invalid UUID lookup submits a ready invalid value', async () => {
    const context = harness([response(400, { code: 'invalid-request' })], {
        scenarios: { invalidId: { kind: 'invalidId', expectedHttp: 400 } }
    });
    await context.runner.run('invalidId');
    assert.equal(context.requests[0].url, '/api/v1/requests/invalid-uuid');
});

test('paginated search prepares an isolated customer and validates the created item', async () => {
    const context = harness([
        response(201, { id: 'listed-id', status: 'PENDING' }),
        response(200, { items: [{ id: 'listed-id' }], totalElements: 1 })
    ], { scenarios: { ...settings.scenarios, list: { kind: 'list', expectedHttp: 200 } } });
    await context.runner.run('list');
    const created = JSON.parse(context.requests[0].body);
    assert.notEqual(created.customerId, body.customerId);
    assert.equal(context.requests[1].url, `/api/v1/requests?customerId=${created.customerId}&page=0&size=1`);
});

test('oversized request uses the configured body limit and validates HTTP 413', async () => {
    const context = harness([response(413, { code: 'payload-too-large' })], {
        scenarios: { ...settings.scenarios, oversized: { kind: 'oversized', expectedHttp: 413 } }
    });
    const result = await context.runner.run('oversized');
    assert.ok(Buffer.byteLength(context.requests[0].body) > settings.maxBodyBytes);
    assert.equal(result.steps.at(-1).status, 413);
});

test('rate-limit scenario consumes only the configured quota and observes 429 without retry', async () => {
    const context = harness([
        response(404, {}), response(404, {}), response(429, { code: 'rate-limited' }, { 'retry-after': '1' })
    ], { scenarios: { rateLimit: { kind: 'rateLimit', expectedHttp: 429 } } });
    const result = await context.runner.run('rateLimit');
    assert.equal(result.steps.at(-1).status, 429);
    assert.equal(context.requests.length, 3);
    assert.ok(context.requests.every(request => request.method === 'GET'));
    assert.equal(context.delays.length, 0);
});

test('a new selection after quota exhaustion waits for Retry-After then resumes automatically', async () => {
    const context = harness([
        response(429, { code: 'rate-limited' }, { 'retry-after': '2' }),
        response(201, { id: 'after-quota', status: 'PENDING' }),
        response(200, { id: 'after-quota', status: 'COMPLETED' })
    ], { rateLimitWindowMs: 5000 });
    const result = await context.runner.run('sucesso');
    assert.equal(result.terminalStatus, 'COMPLETED');
    assert.ok(context.delays[0] >= 2000);
    assert.equal(context.requests[0].body, context.requests[1].body);
});

test('a replay returning another record fails instead of claiming successful idempotency', async () => {
    const context = harness([
        response(201, { id: 'original-id', status: 'PENDING' }),
        response(200, { id: 'wrong-id', status: 'COMPLETED' })
    ], { scenarios: { ...settings.scenarios, replay: { kind: 'replay', body, expectedHttp: 200 } } });
    await assert.rejects(context.runner.run('replay'), /identificador/);
});

test('unsupported demo payload size is rejected before allocation or HTTP', async () => {
    const context = harness([], {
        maxBodyBytes: 2 ** 30,
        scenarios: { oversized: { kind: 'oversized', expectedHttp: 413 } }
    });
    await assert.rejects(context.runner.run('oversized'), /limite seguro/);
    assert.equal(context.requests.length, 0);
});

test('a quota too large for an interactive demonstration is rejected before sending traffic', async () => {
    const context = harness([], {
        rateLimitMaxRequests: 1000000,
        scenarios: { rateLimit: { kind: 'rateLimit', expectedHttp: 429 } }
    });
    await assert.rejects(context.runner.run('rateLimit'), /limite seguro/);
    assert.equal(context.requests.length, 0);
});

test('HTTP timeout aborts the transport without resubmitting an uncertain creation', async () => {
    let calls = 0;
    const runner = createRunner({
        settings: { ...settings, requestTimeoutMs: 5 },
        transport: async (url, { signal }) => {
            calls++;
            return new Promise((resolve, reject) => {
                const timer = setTimeout(() => resolve(response(201, { id: 'late', status: 'COMPLETED' })), 100);
                signal.addEventListener('abort', () => { clearTimeout(timer); reject(signal.reason); }, { once: true });
            });
        }
    });
    await assert.rejects(runner.run('sucesso'), { name: 'TimeoutError' });
    assert.equal(calls, 1);
});

test('polling has a deadline even when the provider never reaches a terminal state', async () => {
    let calls = 0;
    const runner = createRunner({
        settings: { ...settings, timeoutMs: 10, rateLimitWindowMs: 10 },
        transport: async () => response(++calls === 1 ? 201 : 200, { id: 'pending-id', status: 'PENDING' })
    });
    await assert.rejects(runner.run('sucesso'), { name: 'TimeoutError' });
    assert.ok(calls > 1);
});

test('cancelling a polling delay stops further queries and never reports completion', async () => {
    const controller = new AbortController();
    let calls = 0;
    const runner = createRunner({
        settings,
        onProgress: () => controller.abort(),
        transport: async () => { calls++; return response(201, { id: 'cancelled-id', status: 'PENDING' }); }
    });
    await assert.rejects(runner.run('sucesso', { signal: controller.signal }), { name: 'AbortError' });
    assert.equal(calls, 1);
});
