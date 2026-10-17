package br.com.sebratel.contract;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Contrato das rotas do estoque-service. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_BASE_URL", matches = ".+")
class EstoqueContractTest extends AbstractServiceContractTest {

    @Override
    String service() {
        return "estoque";
    }
}
