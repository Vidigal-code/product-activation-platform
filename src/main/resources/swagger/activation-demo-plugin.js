(function () {
    "use strict";

    const styles = {
        panel: { margin: "24px 0", padding: "20px", border: "1px solid #aab8c5", borderRadius: "8px", background: "#f4f8fb" },
        controls: { display: "flex", flexWrap: "wrap", alignItems: "end", gap: "12px" },
        field: { display: "flex", flexDirection: "column", gap: "8px", flex: "1 1 280px" },
        select: { minHeight: "40px", width: "100%", maxWidth: "100%" },
        button: { minHeight: "40px", padding: "8px 16px" },
        note: { fontSize: "14px", lineHeight: "1.5", color: "#394959" },
        output: { maxHeight: "320px", overflow: "auto", whiteSpace: "pre-wrap", overflowWrap: "anywhere", padding: "12px", background: "#fff", color: "#172737" },
        status: { fontWeight: "600", lineHeight: "1.5" }
    };

    globalThis.ActivationScenariosPlugin = function (system) {
        const React = system.React;
        const element = React.createElement;

        class ScenarioPanel extends React.Component {
            constructor(props) {
                super(props);
                this.state = { selected: "", phase: "idle", message: "Selecione um exemplo para começar.", steps: [], result: null };
                this.active = null;
            }

            componentWillUnmount() {
                const active = this.active;
                this.active = null;
                active?.abort();
            }

            settings() {
                return system.specSelectors.specJson().toJS()["x-activation-demo"];
            }

            receiveProgress(controller, event) {
                if (this.active === controller) {
                    this.setState(current => ({
                        message: event.message,
                        steps: event.step ? [...current.steps, event.step] : current.steps
                    }));
                }
            }

            async runScenario(scenarioId) {
                if (this.active || !scenarioId) {
                    return;
                }
                const controller = new AbortController();
                this.active = controller;
                this.setState({ selected: scenarioId, phase: "running", message: "Executando o exemplo…", steps: [], result: null });
                try {
                    const runner = globalThis.ActivationDemo.createRunner({
                        settings: this.settings(),
                        onProgress: event => this.receiveProgress(controller, event)
                    });
                    const result = await runner.run(scenarioId, { signal: controller.signal });
                    if (this.active === controller) {
                        this.setState({
                            phase: "completed", result, steps: result.steps,
                            message: result.terminalStatus
                                ? `Cenário validado. Estado final: ${result.terminalStatus}.`
                                : "Cenário validado. As respostas esperadas foram confirmadas."
                        });
                    }
                } catch (error) {
                    this.showFailure(controller, error);
                } finally {
                    if (this.active === controller) {
                        this.active = null;
                    }
                }
            }

            showFailure(controller, error) {
                if (this.active !== controller) {
                    return;
                }
                if (controller.signal.aborted) {
                    this.setState({ phase: "cancelled", message: "Acompanhamento cancelado. Solicitações já registradas continuam no servidor." });
                    return;
                }
                this.setState({ phase: "failed", message: error instanceof Error ? error.message : "Não foi possível concluir o exemplo." });
            }

            cancel() {
                this.active?.abort();
            }

            renderOutput() {
                const { result, steps } = this.state;
                if (!result && steps.length === 0) {
                    return null;
                }
                return element("div", { "aria-label": "Resultado do exemplo" },
                    result?.requestId && element("p", null, "Solicitação: ", element("code", null, result.requestId)),
                    element("details", null,
                        element("summary", { style: styles.note }, `Ver respostas HTTP (${steps.length})`),
                        element("pre", { style: styles.output }, JSON.stringify(result || { steps }, null, 2))));
            }

            render() {
                const settings = this.settings();
                if (!settings?.scenarios) {
                    return null;
                }
                const running = this.state.phase === "running";
                return element("section", { style: styles.panel, "aria-labelledby": "activation-demo-title", "aria-busy": running },
                    element("h3", { id: "activation-demo-title" }, "Exemplos automáticos"),
                    element("p", { style: styles.note }, "Demonstração local: escolha um cenário. Os campos e as consultas são preenchidos automaticamente."),
                    element("div", { style: styles.controls },
                        element("div", { style: styles.field },
                            element("label", { htmlFor: "activation-demo-example" }, "Exemplo automático"),
                            element("select", {
                                id: "activation-demo-example", style: styles.select, value: this.state.selected,
                                disabled: running, onChange: event => this.runScenario(event.target.value)
                            },
                            element("option", { value: "", disabled: true }, "Selecione um cenário"),
                            ...Object.entries(settings.scenarios).map(([id, scenario]) =>
                                element("option", { key: id, value: id }, scenario.label)))),
                        element("button", {
                            type: "button", className: "btn", style: styles.button,
                            disabled: running || !this.state.selected, onClick: () => this.runScenario(this.state.selected)
                        }, "Executar novamente"),
                        element("button", {
                            type: "button", className: "btn", style: styles.button,
                            disabled: !running, onClick: () => this.cancel()
                        }, "Cancelar")),
                    element("p", { role: "status", "aria-live": "polite", "aria-atomic": true, style: styles.status }, this.state.message),
                    this.renderOutput(),
                    element("p", { style: styles.note }, "Cancelar interrompe o acompanhamento; solicitações já gravadas continuam no servidor."));
            }
        }

        return {
            wrapComponents: {
                InfoContainer: Original => props => element(React.Fragment, null,
                    element(Original, props), element(ScenarioPanel))
            }
        };
    };
}());
