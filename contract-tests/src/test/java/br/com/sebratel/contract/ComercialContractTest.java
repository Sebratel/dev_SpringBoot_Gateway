package br.com.sebratel.contract;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Contrato das rotas do comercial-service. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_BASE_URL", matches = ".+")
class ComercialContractTest extends AbstractServiceContractTest {

    @Override
    String service() {
        return "comercial";
    }
}
