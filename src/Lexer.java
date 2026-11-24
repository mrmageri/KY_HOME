import java.util.ArrayList;
import java.util.List;

/**
 * Виды лексем учебного конфигурационного языка.
 */
enum Kind {
    NUM, STR, NAME,
    DICT_OPEN, RBRACK, LBRACE, RBRACE,
    COLON, COMMA, EQ,
    PLUS, MINUS, STAR, SLASH,
    EOF
}

/**
 * Лексема с координатами её начала.
 */
final class Token {
    /** Вид лексемы. */
    final Kind kind;
    /** Исходный текст. */
    final String text;
    /** Номер строки (с 1). */
    final int line;
    /** Номер столбца (с 1). */
    final int col;

    /**
     * @param kind вид
     * @param text текст
     * @param line строка
     * @param col  столбец
     */
    Token(Kind kind, String text, int line, int col) {
        this.kind = kind;
        this.text = text;
        this.line = line;
        this.col = col;
    }

    /**
     * @return читаемое представление для сообщений об ошибках
     */
    String describe() {
        return kind == Kind.EOF ? "конец файла" : "'" + text + "'";
    }
}

/**
 * Лексер: разбивает исходный текст на поток {@link Token}.
 */
final class Lexer {
    private static final int ZERO = 0;
    private static final int ONE = 1;
    private static final int NO_PART = -1;
    private static final int START_LINE = 1;
    private static final int START_COL = 1;

    private final String src;
    private int pos;
    private int line = 1;
    private int col = 1;

    /**
     * @param source исходный текст
     */
    Lexer(String source) {
        this.src = source.startsWith("\uFEFF") ? source.substring(ONE) : source;
    }

    /**
     * @return список лексем с завершающей EOF
     * @throws ConfigException при недопустимых символах
     */
    List<Token> tokenize() throws ConfigException {
        List<Token> out = new ArrayList<>();
        while (true) {
            skipIgnorable();
            if (pos >= src.length()) {
                out.add(new Token(Kind.EOF, "", line, col));
                return out;
            }
            out.add(readToken());
        }
    }

    private Token readToken() throws ConfigException {
        char ch = src.charAt(pos);
        if (ch == 'q' && nextIsOpenParen()) {
            return readString();
        }
        if (Character.isDigit(ch) || ch == '.') {
            return readNumber();
        }
        if (ch == '-') {
            return readMinusOrNegativeNumber();
        }
        if (isNameStart(ch)) {
            return readName();
        }
        if (ch == '$') {
            return readDictOpen();
        }
        return readPunctuation(ch);
    }

    private Token readDictOpen() throws ConfigException {
        int startLine = line;
        int startCol = col;
        if (!nextIsOpenBracket()) {
            throw err("ожидалось '$['", startLine, startCol);
        }
        advance();
        advance();
        return new Token(Kind.DICT_OPEN, "$[", startLine, startCol);
    }

    private Token readMinusOrNegativeNumber() throws ConfigException {
        int startLine = line;
        int startCol = col;
        int next = pos + ONE;
        if (next < src.length() && startsNumber(next)) {
            return readNumber();
        }
        advance();
        return new Token(Kind.MINUS, "-", startLine, startCol);
    }

    private boolean startsNumber(int at) {
        char ch = src.charAt(at);
        return Character.isDigit(ch) || ch == '.';
    }

    private Token readPunctuation(char ch) throws ConfigException {
        int startLine = line;
        int startCol = col;
        Kind kind = punctuationKind(ch);
        if (kind == null) {
            kind = operatorKind(ch);
        }
        if (kind == null) {
            throw err("недопустимый символ '" + ch + "'", startLine, startCol);
        }
        advance();
        return new Token(kind, String.valueOf(ch), startLine, startCol);
    }

    private Kind punctuationKind(char ch) {
        switch (ch) {
            case ']': return Kind.RBRACK;
            case '{': return Kind.LBRACE;
            case '}': return Kind.RBRACE;
            case ':': return Kind.COLON;
            case ',': return Kind.COMMA;
            case '=': return Kind.EQ;
            default:  return null;
        }
    }

    private Kind operatorKind(char ch) {
        switch (ch) {
            case '+': return Kind.PLUS;
            case '*': return Kind.STAR;
            case '/': return Kind.SLASH;
            default:  return null;
        }
    }

    private Token readName() {
        int startLine = line;
        int startCol = col;
        int start = pos;
        while (pos < src.length() && isNamePart(src.charAt(pos))) {
            advance();
        }
        return new Token(Kind.NAME, src.substring(start, pos), startLine, startCol);
    }

    private Token readString() throws ConfigException {
        int startLine = line;
        int startCol = col;
        advance();
        advance();
        int start = pos;
        while (pos < src.length() && src.charAt(pos) != ')') {
            advance();
        }
        if (pos >= src.length()) {
            throw err("незакрытая строка (нет закрывающей ')')", startLine, startCol);
        }
        String content = src.substring(start, pos);
        advance();
        return new Token(Kind.STR, content, startLine, startCol);
    }

    private Token readNumber() throws ConfigException {
        int startLine = line;
        int startCol = col;
        int start = pos;
        if (src.charAt(pos) == '-') {
            advance();
        }
        int intDigits = consumeDigits();
        int fracDigits = consumeFraction();
        int expDigits = consumeExponent();
        validateNumber(intDigits, fracDigits, expDigits, startLine, startCol);
        return new Token(Kind.NUM, src.substring(start, pos), startLine, startCol);
    }

    private int consumeDigits() {
        int count = ZERO;
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            advance();
            count++;
        }
        return count;
    }

    private int consumeFraction() {
        if (pos < src.length() && src.charAt(pos) == '.') {
            advance();
            return consumeDigits();
        }
        return NO_PART;
    }

    private int consumeExponent() {
        if (pos >= src.length()) {
            return NO_PART;
        }
        char ch = src.charAt(pos);
        if (ch != 'e' && ch != 'E') {
            return NO_PART;
        }
        advance();
        if (pos < src.length()) {
            char sign = src.charAt(pos);
            if (sign == '+' || sign == '-') {
                advance();
            }
        }
        return consumeDigits();
    }

    private void validateNumber(int intDigits, int fracDigits,
                                int expDigits, int startLine, int startCol)
            throws ConfigException {
        boolean hasDot = fracDigits != NO_PART;
        boolean hasExp = expDigits != NO_PART;
        if (!hasDot && intDigits == ZERO) {
            throw err("некорректное число", startLine, startCol);
        }
        if (hasDot && intDigits == ZERO && fracDigits == ZERO) {
            throw err("некорректное число", startLine, startCol);
        }
        if (hasExp && expDigits == ZERO) {
            throw err("некорректный показатель степени", startLine, startCol);
        }
    }

    private void skipIgnorable() {
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (Character.isWhitespace(ch)) {
                advance();
            } else if (ch == '\\') {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    advance();
                }
            } else {
                return;
            }
        }
    }

    private boolean nextIsOpenParen() {
        return pos + ONE < src.length() && src.charAt(pos + ONE) == '(';
    }

    private boolean nextIsOpenBracket() {
        return pos + ONE < src.length() && src.charAt(pos + ONE) == '[';
    }

    private void advance() {
        char ch = src.charAt(pos++);
        if (ch == '\n') {
            line++;
            col = START_COL;
        } else {
            col++;
        }
    }

    private static boolean isNameStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private static boolean isNamePart(char ch) {
        return isNameStart(ch);
    }

    private static ConfigException err(String message, int atLine, int atCol) {
        return new ConfigException(
                "Ошибка (строка " + atLine + ", столбец " + atCol + "): " + message);
    }
}