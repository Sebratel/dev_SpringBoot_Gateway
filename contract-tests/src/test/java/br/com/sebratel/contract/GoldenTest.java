package br.com.sebratel.contract;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenTest {

    @Test
    void nullFieldAgainstTypedFieldIsTheSameContract() {
        Golden recorded = new Golden(200, List.of("$.data[].finalizado:null", "$.data[].id:number"));
        Golden now = new Golden(200, List.of("$.data[].finalizado:string", "$.data[].id:number"));

        assertTrue(recorded.sameContractAs(now));
        assertTrue(now.sameContractAs(recorded));
    }

    @Test
    void emptyListAgainstFilledListIsTheSameContract() {
        Golden recorded = new Golden(200, List.of("$:[]"));
        Golden now = new Golden(200, List.of("$[].countCallingstation:number", "$[].username:string"));

        assertTrue(recorded.sameContractAs(now));
        assertTrue(now.sameContractAs(recorded));
    }

    @Test
    void differentStatusIsADifference() {
        assertFalse(new Golden(200, List.of("$:[]")).sameContractAs(new Golden(500, List.of("$:[]"))));
    }

    @Test
    void aMissingFieldIsADifference() {
        Golden recorded = new Golden(200, List.of("$.a:string", "$.b:number"));
        Golden now = new Golden(200, List.of("$.a:string"));

        assertFalse(recorded.sameContractAs(now));
    }

    @Test
    void aChangedTypeIsADifference() {
        Golden recorded = new Golden(200, List.of("$.a:string"));
        Golden now = new Golden(200, List.of("$.a:number"));

        assertFalse(recorded.sameContractAs(now));
    }
}
