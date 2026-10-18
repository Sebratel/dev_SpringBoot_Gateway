package br.com.sebratel.contract;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Resposta de referencia gravada de uma rota: status HTTP e formato do corpo. */
public record Golden(int status, List<String> shape) {

    private static final String NULL_SUFFIX = ":null";
    private static final String EMPTY_LIST_SUFFIX = ":[]";

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
        Set<String> emptyLists = new HashSet<>();
        collectEmptyListPaths(shape, emptyLists);
        collectEmptyListPaths(other.shape, emptyLists);
        return normalize(shape, nullable, emptyLists).equals(normalize(other.shape, nullable, emptyLists));
    }

    /** Lista vazia de um lado e preenchida do outro: o dado mudou, o contrato nao; o conteudo da lista nao e comparado. */
    private static void collectEmptyListPaths(List<String> shape, Set<String> out) {
        for (String entry : shape) {
            if (entry.endsWith(EMPTY_LIST_SUFFIX)) {
                out.add(entry.substring(0, entry.length() - EMPTY_LIST_SUFFIX.length()));
            }
        }
    }

    private static void collectNullablePaths(List<String> shape, Set<String> out) {
        for (String entry : shape) {
            if (entry.endsWith(NULL_SUFFIX)) {
                out.add(entry.substring(0, entry.length() - NULL_SUFFIX.length()));
            }
        }
    }

    private static Set<String> normalize(List<String> shape, Set<String> nullable, Set<String> emptyLists) {
        Set<String> normalized = new TreeSet<>();
        for (String entry : shape) {
            if (underEmptyList(entry, emptyLists)) {
                continue;
            }
            int colon = entry.lastIndexOf(':');
            String path = colon < 0 ? entry : entry.substring(0, colon);
            normalized.add(nullable.contains(path) ? path + ":*" : entry);
        }
        return normalized;
    }

    private static boolean underEmptyList(String entry, Set<String> emptyLists) {
        for (String list : emptyLists) {
            if (entry.equals(list + EMPTY_LIST_SUFFIX) || entry.startsWith(list + "[]")) {
                return true;
            }
        }
        return false;
    }
}
