import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Рекурсивный парсер учебного конфигурационного языка.
 */
final class Parser {
    private static final String VAR_KEYWORD = "var";

    private final List<Token> tokens;
    private int pos;
    private final Map<String, Value> constants = new LinkedHashMap<>();
    private final Map<String, Value> root = new LinkedHashMap<>();

    /**
     * @param tokens поток лексем
     */
    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * @return корневой словарь TOML-документа
     * @throws ConfigException при синтаксических ошибках
     */
    Map<String, Value> parseProgram() throws ConfigException {
        while (peek().kind != Kind.EOF) {
            parseTopLevelItem();
        }
        return root;
    }

    private void parseTopLevelItem() throws ConfigException {
        Token t = peek();
        if (isVarDeclaration(t)) {
            parseVarDeclaration();
            return;
        }
        if (t.kind == Kind.DICT_OPEN) {
            parseTopLevelDict();
            return;
        }
        throw err("ожидалось 'var' или '$[', получено " + t.describe(), t);
    }

    private boolean isVarDeclaration(Token t) {
        return t.kind == Kind.NAME && t.text.equals(VAR_KEYWORD);
    }

    private void parseVarDeclaration() throws ConfigException {
        next();
        Token name = expect(Kind.NAME, "ожидалось имя константы");
        if (constants.containsKey(name.text)) {
            throw err("константа '" + name.text + "' уже объявлена", name);
        }
        expect(Kind.EQ, "ожидалось '=' после имени константы");
        constants.put(name.text, parseValue(true));
    }

    private void parseTopLevelDict() throws ConfigException {
        Token open = next();
        VDict dict = parseDictBody();
        for (Map.Entry<String, Value> e : dict.entries().entrySet()) {
            if (root.containsKey(e.getKey())) {
                throw err("ключ '" + e.getKey() + "' уже определён", open);
            }
            root.put(e.getKey(), e.getValue());
        }
    }

    private VDict parseDictBody() throws ConfigException {
        VDict dict = new VDict();
        if (tryConsume(Kind.RBRACK)) {
            return dict;
        }
        while (true) {
            parseDictEntry(dict);
            if (tryConsume(Kind.RBRACK)) {
                return dict;
            }
            expect(Kind.COMMA, "ожидалось ',' или ']'");
            if (tryConsume(Kind.RBRACK)) {
                return dict;
            }
        }
    }

    private void parseDictEntry(VDict dict) throws ConfigException {
        Token name = expect(Kind.NAME, "ожидалось имя поля");
        if (dict.containsKey(name.text)) {
            throw err("дубликат ключа '" + name.text + "'", name);
        }
        expect(Kind.COLON, "ожидалось ':' после имени поля");
        dict.put(name.text, parseValue(true));
    }

    private Value parseValue(boolean allowDict) throws ConfigException {
        Token t = peek();
        switch (t.kind) {
            case NUM:       return numberValue(next());
            case STR:       return new VStr(next().text);
            case NAME:      return constantValue(next());
            case LBRACE:    return parseExpression();
            case DICT_OPEN: return parseNestedDict(allowDict, t);
            default:        throw err("ожидалось значение, получено " + t.describe(), t);
        }
    }

    private Value parseNestedDict(boolean allowDict, Token open) throws ConfigException {
        if (!allowDict) {
            throw err("словарь не может быть операндом выражения", open);
        }
        next();
        return parseDictBody();
    }

    private Value constantValue(Token t) throws ConfigException {
        Value v = constants.get(t.text);
        if (v == null) {
            throw err("неизвестная константа '" + t.text + "'", t);
        }
        return v;
    }

    private Value numberValue(Token t) throws ConfigException {
        return NumberLiteral.parse(t.text, t);
    }

    private Value parseExpression() throws ConfigException {
        Token open = expect(Kind.LBRACE, "ожидалось '{'");
        String op = readOperator(open);
        List<Value> args = readOperands(open);
        return Evaluator.evaluate(op, args, open);
    }

    private String readOperator(Token open) throws ConfigException {
        Token t = next();
        String op = operatorName(t);
        if (op == null) {
            throw err("ожидалась операция (+, -, *, /, chr, pow), получено "
                    + t.describe(), t);
        }
        return op;
    }

    private String operatorName(Token t) {
        switch (t.kind) {
            case PLUS:  return "+";
            case MINUS: return "-";
            case STAR:  return "*";
            case SLASH: return "/";
            case NAME:  return t.text;
            default:    return null;
        }
    }

    private List<Value> readOperands(Token open) throws ConfigException {
        List<Value> args = new ArrayList<>();
        while (peek().kind != Kind.RBRACE) {
            if (peek().kind == Kind.EOF) {
                throw err("незакрытое выражение (нет '}')", open);
            }
            args.add(parseValue(false));
        }
        next();
        return args;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token next() {
        return tokens.get(pos++);
    }

    private boolean tryConsume(Kind kind) {
        if (peek().kind == kind) {
            next();
            return true;
        }
        return false;
    }

    private Token expect(Kind kind, String message) throws ConfigException {
        Token t = peek();
        if (t.kind != kind) {
            throw err(message + ", получено " + t.describe(), t);
        }
        return next();
    }

    private static ConfigException err(String message, Token t) {
        return new ConfigException(
                "Ошибка (строка " + t.line + ", столбец " + t.col + "): " + message);
    }
}

/**
 * Преобразование текстового литерала числа в {@link VNum}.
 */
final class NumberLiteral {
    private static final int ZERO = 0;
    private static final int ONE = 1;
    private static final int NO_INDEX = -1;
    private static final char DOT = '.';
    private static final char MINUS = '-';
    private static final char EXP_LOWER = 'e';
    private static final char EXP_UPPER = 'E';

    private NumberLiteral() {
    }

    /**
     * @param text исходный литерал
     * @param at   позиция для сообщений
     * @return числовое значение
     * @throws ConfigException при выходе за диапазон long
     */
    static VNum parse(String text, Token at) throws ConfigException {
        String normalized = normalize(text);
        if (isInteger(normalized)) {
            return parseInt(normalized, text, at);
        }
        return VNum.ofNormalized(normalized, Double.parseDouble(normalized));
    }

    private static boolean isInteger(String s) {
        return s.indexOf(DOT) < ZERO
                && s.indexOf(EXP_LOWER) < ZERO
                && s.indexOf(EXP_UPPER) < ZERO;
    }

    private static VNum parseInt(String normalized, String original, Token at)
            throws ConfigException {
        try {
            return VNum.ofLong(Long.parseLong(normalized));
        } catch (NumberFormatException e) {
            throw new ConfigException(
                    "Ошибка (строка " + at.line + ", столбец " + at.col + "): "
                            + "число вне допустимого диапазона: " + original);
        }
    }

    private static String normalize(String text) {
        boolean negative = text.charAt(ZERO) == MINUS;
        String body = negative ? text.substring(ONE) : text;
        String exponent = extractExponent(body);
        body = removeExponent(body);
        int dot = body.indexOf(DOT);
        String intPart = dot < ZERO ? body : body.substring(ZERO, dot);
        String fracPart = dot < ZERO ? null : body.substring(dot + ONE);
        intPart = stripLeadingZeros(intPart);
        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append(MINUS);
        }
        sb.append(intPart.isEmpty() ? "0" : intPart);
        if (fracPart != null) {
            sb.append(DOT).append(fracPart.isEmpty() ? "0" : fracPart);
        }
        if (!exponent.isEmpty()) {
            sb.append(EXP_LOWER).append(exponent);
        }
        return sb.toString();
    }

    private static String extractExponent(String body) {
        int idx = indexOfExponent(body);
        return idx < ZERO ? "" : body.substring(idx + ONE);
    }

    private static String removeExponent(String body) {
        int idx = indexOfExponent(body);
        return idx < ZERO ? body : body.substring(ZERO, idx);
    }

    private static int indexOfExponent(String body) {
        for (int i = ZERO; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == EXP_LOWER || c == EXP_UPPER) {
                return i;
            }
        }
        return NO_INDEX;
    }

    private static String stripLeadingZeros(String s) {
        int i = ZERO;
        while (i < s.length() - ONE && s.charAt(i) == '0') {
            i++;
        }
        return s.substring(i);
    }
}