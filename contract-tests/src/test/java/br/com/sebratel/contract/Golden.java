package br.com.sebratel.contract;

import java.util.List;

/** Resposta de referencia gravada de uma rota: status HTTP e formato do corpo. */
public record Golden(int status, List<String> shape) {
}
