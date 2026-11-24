import java.util.Map;

/**
 * Преобразователь дерева значений в текст TOML.
 */
final class TomlWriter {
    private static final char NEWLINE = '\n';
    private static final char QUOTE = '"';
    private static final int MIN_CONTROL_CODE = 0x20;
    private static final int DELETE_CODE = 0x7F;

    private TomlWriter() {
    }

    /**
     * @param root корневой словарь
     * @return текст TOML-документа
     */
    static String write(Map<String, Value> root) {
        StringBuilder sb = new StringBuilder();
        appendScalars(sb, root);
        appendTables(sb, root);
        return sb.toString();
    }

    private static void appendScalars(StringBuilder sb, Map<String, Value> map) {
        for (Map.Entry<String, Value> e : map.entrySet()) {
            if (!(e.getValue() instanceof VDict)) {
                sb.append(e.getKey()).append(" = ")
                        .append(scalar(e.getValue())).append(NEWLINE);
            }
        }
    }

    private static void appendTables(StringBuilder sb, Map<String, Value> map) {
        for (Map.Entry<String, Value> e : map.entrySet()) {
            if (e.getValue() instanceof VDict) {
                appendTable(sb, e.getKey(), (VDict) e.getValue());
            }
        }
    }

    private static void appendTable(StringBuilder sb, String path, VDict dict) {
        if (!sb.isEmpty()) {
            sb.append(NEWLINE);
        }
        sb.append('[').append(path).append(']').append(NEWLINE);
        appendScalars(sb, dict.entries());
        for (Map.Entry<String, Value> e : dict.entries().entrySet()) {
            if (e.getValue() instanceof VDict) {
                appendTable(sb, path + "." + e.getKey(), (VDict) e.getValue());
            }
        }
    }

    private static String scalar(Value v) {
        if (v instanceof VNum) {
            return ((VNum) v).toml;
        }
        if (v instanceof VStr) {
            return tomlString(((VStr) v).text);
        }
        throw new IllegalStateException("словарь не является скалярным значением");
    }

    private static String tomlString(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append(QUOTE);
        for (int i = 0; i < s.length(); i++) {
            appendChar(sb, s.charAt(i));
        }
        sb.append(QUOTE);
        return sb.toString();
    }

    private static void appendChar(StringBuilder sb, char c) {
        switch (c) {
            case '"':  sb.append("\\\""); return;
            case '\\': sb.append("\\\\"); return;
            case '\n': sb.append("\\n");  return;
            case '\r': sb.append("\\r");  return;
            case '\t': sb.append("\\t");  return;
            case '\b': sb.append("\\b");  return;
            case '\f': sb.append("\\f");  return;
            default:   appendDefault(sb, c);
        }
    }

    private static void appendDefault(StringBuilder sb, char c) {
        if (c < MIN_CONTROL_CODE || c == DELETE_CODE) {
            sb.append(String.format("\\u%04X", (int) c));
        } else {
            sb.append(c);
        }
    }
}