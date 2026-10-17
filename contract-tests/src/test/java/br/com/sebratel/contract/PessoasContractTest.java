package br.com.sebratel.contract;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Contrato das rotas do pessoas-service. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_BASE_URL", matches = ".+")
class PessoasContractTest extends AbstractServiceContractTest {

    @Override
    String service() {
        return "pessoas";
    }
}
