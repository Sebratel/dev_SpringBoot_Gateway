package br.com.sebratel.contract;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Resposta de referencia gravada de uma rota: status HTTP e formato do corpo. */
public record Golden(int status, List<String> shape) {

    private static final String NULL_SUFFIX = ":null";

    /**
     * Mesmo contrato: mesmo status e mesmos campos. Um campo que e {@code null} num lado e tem tipo no outro nao
     * conta como divergencia: o formato e tirado do 1o elemento da lista e o dado muda (ex.: data de finalizacao
     * ainda vazia na gravacao e preenchida depois).
     */
    boolean sameContractAs(Golden other) {
        if (status != other.status) {
            return false;
        }
        Set<String> nullable = new HashSet<>();
        collectNullablePaths(shape, nullable);
        collectNullablePaths(other.shape, nullable);
        return normalize(shape, nullable).equals(normalize(other.shape, nullable));
    }

    private static void collectNullablePaths(List<String> shape, Set<String> out) {
        for (String entry : shape) {
            if (entry.endsWith(NULL_SUFFIX)) {
                out.add(entry.substring(0, entry.length() - NULL_SUFFIX.length()));
            }
        }
    }

    private static Set<String> normalize(List<String> shape, Set<String> nullable) {
        Set<String> normalized = new TreeSet<>();
        for (String entry : shape) {
            int colon = entry.lastIndexOf(':');
            String path = colon < 0 ? entry : entry.substring(0, colon);
            normalized.add(nullable.contains(path) ? path + ":*" : entry);
        }
        return normalized;
    }
}
