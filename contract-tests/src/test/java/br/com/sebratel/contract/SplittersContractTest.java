package br.com.sebratel.contract;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Contrato das rotas do splitters-service. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_BASE_URL", matches = ".+")
class SplittersContractTest extends AbstractServiceContractTest {

    @Override
    String service() {
        return "splitters";
    }
}
