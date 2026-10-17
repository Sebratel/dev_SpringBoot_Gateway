package br.com.sebratel.contract;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Contrato das rotas do radius-service. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_BASE_URL", matches = ".+")
class RadiusContractTest extends AbstractServiceContractTest {

    @Override
    String service() {
        return "radius";
    }
}
