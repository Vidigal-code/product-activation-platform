(function () {
    'use strict';

    const MAX_DEMO_BODY_BYTES = 1024 * 1024;
    const MAX_DEMO_RATE_REQUESTS = 1000;

    async function transport(url, options) {
        const response = await fetch(url, options);
        return { status: response.status, body: await response.json(), headers: response.headers };
    }

    function wait(milliseconds, signal) {
        signal.throwIfAborted();
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                signal.removeEventListener('abort', abort);
                resolve();
            }, milliseconds);
            function abort() {
                clearTimeout(timer);
                reject(signal.reason);
            }
            signal.addEventListener('abort', abort, { once: true });
        });
    }

    function expectHttp(response, expected) {
        if (response.status !== expected) {
            throw new Error(`Esperado HTTP ${expected}; recebido HTTP ${response.status}.`);
        }
        return response;
    }

    function createRunner(options) {
        const settings = options.settings;
        const send = options.transport || transport;
        const pause = options.wait || wait;
        const uuid = options.uuid || (() => crypto.randomUUID());
        const progress = options.onProgress || (() => {});

        async function run(key, execution = {}) {
            const deadline = AbortSignal.timeout(settings.timeoutMs + settings.rateLimitWindowMs);
            const signal = execution.signal ? AbortSignal.any([deadline, execution.signal]) : deadline;
            signal.throwIfAborted();
            const scenario = settings.scenarios[key];
            const steps = [];
            async function sendOnce(method, url, body) {
                signal.throwIfAborted();
                const response = await send(url, {
                    method, credentials: 'omit', redirect: 'error',
                    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
                    body: body === undefined ? undefined : JSON.stringify(body),
                    signal: AbortSignal.any([signal, AbortSignal.timeout(settings.requestTimeoutMs)])
                });
                const step = { method, url, status: response.status, body: response.body };
                steps.push(step);
                progress({ message: `${method} ${url}: HTTP ${response.status}`, step });
                return response;
            }
            async function request(method, url, body) {
                const response = await sendOnce(method, url, body);
                if (response.status !== 429 || scenario.kind === 'rateLimit') {
                    return response;
                }
                const value = response.headers?.get?.('retry-after') ?? response.headers?.['retry-after'];
                const seconds = Number(value);
                const delay = Number.isFinite(seconds) && seconds > 0 ? seconds * 1000 : settings.rateLimitWindowMs;
                progress({ message: 'Cota temporariamente esgotada. Aguardando Retry-After para continuar.' });
                await pause(delay, signal);
                return sendOnce(method, url, body);
            }
            async function immediateScenario() {
                if (scenario.kind === 'rateLimit') {
                    if (settings.rateLimitMaxRequests > MAX_DEMO_RATE_REQUESTS) {
                        throw new Error('A cota configurada excede o limite seguro desta demonstração (1000 chamadas).');
                    }
                    const url = `${settings.path}/${uuid()}`;
                    for (let attempt = 0; attempt <= settings.rateLimitMaxRequests; attempt++) {
                        const response = await request('GET', url);
                        if (response.status === scenario.expectedHttp) {
                            return { scenario: key, label: scenario.label, steps };
                        }
                        expectHttp(response, 404);
                    }
                    throw new Error('O limite de chamadas não foi observado dentro da cota configurada.');
                }
                if (['missing', 'invalidId'].includes(scenario.kind)) {
                    const id = scenario.kind === 'missing' ? uuid() : 'invalid-uuid';
                    expectHttp(await request('GET', `${settings.path}/${id}`), scenario.expectedHttp);
                    return { scenario: key, label: scenario.label, steps };
                }
                if (scenario.kind === 'oversized') {
                    if (settings.maxBodyBytes > MAX_DEMO_BODY_BYTES) {
                        throw new Error('O corpo configurado excede o limite seguro desta demonstração (1 MiB).');
                    }
                    const oversized = { ...settings.scenarios.sucesso.body, padding: 'x'.repeat(settings.maxBodyBytes) };
                    expectHttp(await request('POST', settings.path, oversized), scenario.expectedHttp);
                    return { scenario: key, label: scenario.label, steps };
                }
                if (scenario.kind === 'invalid') {
                    expectHttp(await request('POST', settings.path, scenario.body), scenario.expectedHttp);
                    return { scenario: key, label: scenario.label, steps };
                }
            }
            async function activationScenario() {
                const template = ['conflict', 'lookup', 'list'].includes(scenario.kind) ? settings.scenarios.sucesso.body : scenario.body;
                const body = { ...template, externalId: `swagger-${uuid()}` };
                if (scenario.kind === 'list') {
                    body.customerId = `swagger-customer-${uuid()}`;
                }
                const created = await request('POST', settings.path, body);
                expectHttp(created, 201);
                const requestId = created.body.id;
                if (scenario.kind === 'list') {
                    const query = new URLSearchParams({ customerId: body.customerId, page: '0', size: '1' });
                    const page = expectHttp(await request('GET', `${settings.path}?${query}`), scenario.expectedHttp);
                    if (!page.body.items.some(item => item.id === requestId)) {
                        throw new Error('A listagem não retornou a solicitação criada.');
                    }
                    return { scenario: key, label: scenario.label, steps, requestId };
                }
                if (scenario.kind === 'conflict') {
                    expectHttp(await request('POST', settings.path, { ...scenario.body, externalId: body.externalId }), scenario.expectedHttp);
                    return { scenario: key, label: scenario.label, steps, requestId };
                }
                let result = created;
                if (scenario.kind === 'lookup') {
                    result = expectHttp(await request('GET', `${settings.path}/${encodeURIComponent(requestId)}`), scenario.expectedHttp);
                }
                if (scenario.kind === 'replay') {
                    result = await request('POST', settings.path, body);
                    expectHttp(result, scenario.expectedHttp);
                    if (result.body.id !== requestId) {
                        throw new Error('A repetição retornou outro identificador.');
                    }
                }
                while (!['COMPLETED', 'FAILED'].includes(result.body.status)) {
                    await pause(settings.pollIntervalMs, signal);
                    result = await request('GET', `${settings.path}/${encodeURIComponent(requestId)}`);
                    expectHttp(result, 200);
                }
                if (scenario.expectedStatus && result.body.status !== scenario.expectedStatus) {
                    throw new Error(`Estado esperado: ${scenario.expectedStatus}; recebido: ${result.body.status}.`);
                }
                if (scenario.expectedFailureCode && result.body.failureCode !== scenario.expectedFailureCode) {
                    throw new Error(`Falha esperada: ${scenario.expectedFailureCode}; recebida: ${result.body.failureCode}.`);
                }
                return { scenario: key, label: scenario.label, steps, requestId, terminalStatus: result.body.status };
            }
            return await immediateScenario() || await activationScenario();
        }
        return { run };
    }

    const api = { createRunner };
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    } else {
        globalThis.ActivationDemo = api;
    }
})();
