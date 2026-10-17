package br.com.sebratel.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

/**
 * Reduz um corpo de resposta ao seu "formato": o conjunto ordenado de caminhos de campos, sem valores.
 * Valores mudam a cada chamada; o que o gateway nao pode mudar e a estrutura.
 */
final class ShapeExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ShapeExtractor() {
    }

    static TreeSet<String> of(String body) {
        TreeSet<String> paths = new TreeSet<>();
        if (body == null || body.isBlank()) {
            paths.add("<empty>");
            return paths;
        }
        try {
            collect(MAPPER.readTree(body), "$", paths);
        } catch (Exception e) {
            paths.add("<text>");
        }
        return paths;
    }

    private static void collect(JsonNode node, String path, TreeSet<String> out) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                collect(field.getValue(), path + "." + field.getKey(), out);
            }
            if (node.isEmpty()) {
                out.add(path + ":{}");
            }
        } else if (node.isArray()) {
            if (node.isEmpty()) {
                out.add(path + ":[]");
            } else {
                collect(node.get(0), path + "[]", out);
            }
        } else {
            out.add(path + ":" + node.getNodeType().name().toLowerCase());
        }
    }
}
