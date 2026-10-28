const { test } = require('node:test');
const assert = require('node:assert/strict');
const { mkdir, writeFile } = require('node:fs/promises');
const { setTimeout: delay } = require('node:timers/promises');
const path = require('node:path');
const { chromium } = require('playwright');

const baseUrl = process.env.SWAGGER_E2E_BASE_URL || 'http://localhost:8080';
const artifactDirectory = path.resolve('target/swagger-e2e');
const scenarioTimeout = 90_000;

async function openDemo(context, artifactName) {
    await mkdir(artifactDirectory, { recursive: true });
    const browser = await chromium.launch({ channel: process.env.SWAGGER_E2E_BROWSER_CHANNEL });
    let page;
    context.after(async () => {
        try {
            if (page && !page.isClosed()) {
                await page.screenshot({ path: path.join(artifactDirectory, `${artifactName}.png`), fullPage: true });
            }
        } finally {
            await browser.close();
        }
    });
    page = await browser.newPage();
    await page.goto(new URL('/swagger-ui/index.html', baseUrl).href);
    await page.getByLabel('Exemplo automático', { exact: true }).waitFor({ timeout: 10_000 });
    return page;
}

async function completedResult(page) {
    await page.getByRole('button', { name: 'Executar novamente', exact: true })
        .and(page.locator(':enabled')).waitFor({ timeout: scenarioTimeout });
    assert.match(await page.getByRole('status').innerText(), /^Cenário validado\./);
    const output = page.getByLabel('Resultado do exemplo', { exact: true });
    await output.getByText(/^Ver respostas HTTP/).click();
    return JSON.parse(await output.locator('pre').innerText());
}

async function runScenario(page, key) {
    await page.getByLabel('Exemplo automático', { exact: true }).selectOption(key);
    return completedResult(page);
}

test('Swagger exposes the sixteen automatic examples through an accessible selector', { timeout: 30_000 }, async (context) => {
    const page = await openDemo(context, 'automatic-selector');
    const selector = page.getByLabel('Exemplo automático', { exact: true });
    assert.equal(await selector.isEnabled(), true);
    const options = await selector.locator('option').evaluateAll(elements => elements.map(option => option.value).filter(Boolean));
    assert.deepEqual(options.sort(), [
        'sucesso', 'repeticao', 'conflito', 'erroNegocio', 'falhaTemporaria', 'timeout', 'limiteProvedor', 'indisponivel',
        'validacao', 'campoDesconhecido', 'lookup', 'missing', 'invalidId', 'list', 'oversized', 'rateLimit'
    ].sort());
});

test('the conflict example works without a prior example or manual identifier', { timeout: scenarioTimeout }, async (context) => {
    const page = await openDemo(context, 'conflict');
    const result = await runScenario(page, 'conflito');
    assert.deepEqual(result.steps.map(step => step.status), [201, 409]);
    assert.equal(result.requestId, result.steps[0].body.id);
});

test('the repetition example creates its own prerequisite and reuses the same request', { timeout: scenarioTimeout }, async (context) => {
    const page = await openDemo(context, 'repetition');
    const result = await runScenario(page, 'repeticao');
    assert.deepEqual(result.steps.slice(0, 2).map(step => step.status), [201, 200]);
    assert.equal(result.steps[0].body.id, result.steps[1].body.id);
    assert.equal(result.terminalStatus, 'COMPLETED');
});

test('running the successful example again creates a fresh completed request', { timeout: scenarioTimeout }, async (context) => {
    const page = await openDemo(context, 'successful-rerun');
    const first = await runScenario(page, 'sucesso');
    const creation = page.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname === '/api/v1/requests' && response.status() === 201);
    await page.getByRole('button', { name: 'Executar novamente', exact: true }).click();
    await creation;
    const second = await completedResult(page);
    assert.equal(first.terminalStatus, 'COMPLETED');
    assert.equal(second.terminalStatus, 'COMPLETED');
    assert.notEqual(first.requestId, second.requestId);
    assert.notEqual(first.steps[0].body.externalId, second.steps[0].body.externalId);
});

test('invalid input is presented as the expected rejection without an activation identifier', { timeout: scenarioTimeout }, async (context) => {
    const page = await openDemo(context, 'validation');
    const result = await runScenario(page, 'validacao');
    assert.deepEqual(result.steps.map(step => step.status), [400]);
    assert.equal(result.requestId, undefined);
    assert.equal(result.steps[0].body.status, 400);
});

test('the provider timeout example displays the persisted uncertain failure', { timeout: scenarioTimeout }, async (context) => {
    const page = await openDemo(context, 'provider-timeout');
    const result = await runScenario(page, 'timeout');
    assert.equal(result.terminalStatus, 'FAILED');
    assert.equal(result.steps.at(-1).body.failureCode, 'PROVIDER_IO_UNCERTAIN');
    assert.equal(result.steps.at(-1).body.attemptCount, 1);
});

test('cancelling observation never reports success when the server finishes later', { timeout: scenarioTimeout }, async (context) => {
    const page = await openDemo(context, 'cancelled');
    const creation = page.waitForResponse(response => response.request().method() === 'POST'
        && new URL(response.url()).pathname === '/api/v1/requests' && response.status() === 201);
    await page.getByLabel('Exemplo automático', { exact: true }).selectOption('timeout');
    const created = await (await creation).json();
    await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await page.getByRole('status').filter({ hasText: /^Acompanhamento cancelado\./ }).waitFor();
    const deadline = Date.now() + 30_000;
    let observedStatus;
    while (Date.now() < deadline && observedStatus !== 'FAILED') {
        const response = await page.request.get(new URL(`/api/v1/requests/${created.id}`, baseUrl).href);
        assert.equal(response.status(), 200);
        observedStatus = (await response.json()).status;
        if (observedStatus !== 'FAILED') await delay(200);
    }
    assert.equal(observedStatus, 'FAILED');
    assert.match(await page.getByRole('status').innerText(), /^Acompanhamento cancelado\./);
    assert.equal(await page.getByLabel('Exemplo automático', { exact: true }).isEnabled(), true);
});

test('all automatic examples execute through the browser and recover after exhausting the quota', { timeout: 300_000 }, async (context) => {
    const page = await openDemo(context, 'all-examples-quota-recovered');
    const cases = [
        { key: 'sucesso', terminal: 'COMPLETED' },
        { key: 'repeticao', prefix: [201, 200], terminal: 'COMPLETED' },
        { key: 'conflito', prefix: [201, 409] },
        { key: 'erroNegocio', terminal: 'FAILED', failure: 'PROVIDER_HTTP_400' },
        { key: 'falhaTemporaria', terminal: 'FAILED', failure: 'PROVIDER_HTTP_500' },
        { key: 'timeout', terminal: 'FAILED', failure: 'PROVIDER_IO_UNCERTAIN' },
        { key: 'limiteProvedor', terminal: 'FAILED', failure: 'PROVIDER_HTTP_429' },
        { key: 'indisponivel', terminal: 'FAILED', failure: 'PROVIDER_HTTP_503' },
        { key: 'validacao', prefix: [400] },
        { key: 'campoDesconhecido', prefix: [400] },
        { key: 'lookup', prefix: [201, 200] },
        { key: 'missing', prefix: [404] },
        { key: 'invalidId', prefix: [400] },
        { key: 'list', prefix: [201, 200] },
        { key: 'oversized', prefix: [413] },
        { key: 'rateLimit', lastHttp: 429 }
    ];
    for (const scenario of cases) {
        await context.test(scenario.key, { timeout: scenarioTimeout }, async () => {
            const result = await runScenario(page, scenario.key);
            assert.equal(result.scenario, scenario.key);
            if (scenario.prefix) {
                assert.deepEqual(result.steps.slice(0, scenario.prefix.length).map(step => step.status), scenario.prefix);
            }
            if (scenario.terminal) assert.equal(result.terminalStatus, scenario.terminal);
            if (scenario.failure) assert.equal(result.steps.at(-1).body.failureCode, scenario.failure);
            if (scenario.lastHttp) assert.equal(result.steps.at(-1).status, scenario.lastHttp);
            await writeFile(path.join(artifactDirectory, `example-${scenario.key}.json`), JSON.stringify(result, null, 2));
        });
    }
    await context.test('successful activation after quota exhaustion', { timeout: scenarioTimeout }, async () => {
        const recovered = await runScenario(page, 'sucesso');
        assert.equal(recovered.terminalStatus, 'COMPLETED');
        assert.ok(recovered.steps.some(step => step.status === 201));
        await writeFile(path.join(artifactDirectory, 'example-quota-recovered.json'), JSON.stringify(recovered, null, 2));
    });
});
