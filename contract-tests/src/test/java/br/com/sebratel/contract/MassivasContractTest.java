package br.com.sebratel.contract;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Contrato das rotas do massivas-service. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_BASE_URL", matches = ".+")
class MassivasContractTest extends AbstractServiceContractTest {

    @Override
    String service() {
        return "massivas";
    }
}
