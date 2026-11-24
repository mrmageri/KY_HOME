import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Фасад преобразования текста учебного языка в TOML.
 */
public final class ConfigToToml {
    private ConfigToToml() {
    }

    /**
     * @param source текст на учебном конфигурационном языке
     * @return текст TOML
     * @throws ConfigException при синтаксических или семантических ошибках
     */
    public static String convert(String source) throws ConfigException {
        List<Token> tokens = new Lexer(source).tokenize();
        Map<String, Value> root = new Parser(tokens).parseProgram();
        return TomlWriter.write(root);
    }
}

/**
 * Значение учебного конфигурационного языка.
 */
interface Value {
}

/**
 * Числовое значение с сохранением целочисленности.
 */
final class VNum implements Value {
    /** TOML-представление. */
    final String toml;
    /** Признак целого. */
    final boolean isInt;
    /** Значение как long. */
    final long lval;
    /** Значение как double. */
    final double dval;

    private VNum(String toml, boolean isInt, long lval, double dval) {
        this.toml = toml;
        this.isInt = isInt;
        this.lval = lval;
        this.dval = dval;
    }

    /**
     * @param v целое значение
     * @return обёртка над целым
     */
    static VNum ofLong(long v) {
        return new VNum(Long.toString(v), true, v, v);
    }

    /**
     * @param v вещественное значение
     * @return обёртка над вещественным
     */
    static VNum ofDouble(double v) {
        return new VNum(Double.toString(v).replace('E', 'e'), false, 0L, v);
    }
    static VNum ofNormalized(String normalizedToml, double dval) {
        return new VNum(normalizedToml, false, 0L, dval);
    }

    /**
     * @return true, если вещественное значение целое по модулю
     */
    boolean hasWholeValue() {
        return !Double.isInfinite(dval) && dval == Math.rint(dval);
    }

    /**
     * @return значение как {@link BigInteger} (для целых)
     */
    BigInteger asBigInteger() {
        return isInt ? BigInteger.valueOf(lval) : BigInteger.ZERO;
    }
}

/**
 * Строковое значение.
 */
final class VStr implements Value {
    /** Текст строки. */
    final String text;

    /**
     * @param text содержимое строки
     */
    VStr(String text) {
        this.text = text;
    }
}

/**
 * Словарь — упорядоченный набор полей «имя — значение».
 */
final class VDict implements Value {
    private final Map<String, Value> entries = new LinkedHashMap<>();

    /**
     * @return содержимое словаря
     */
    Map<String, Value> entries() {
        return entries;
    }

    /**
     * @param key имя поля
     * @return true, если поле присутствует
     */
    boolean containsKey(String key) {
        return entries.containsKey(key);
    }

    /**
     * @param key   имя поля
     * @param value значение
     */
    void put(String key, Value value) {
        entries.put(key, value);
    }
}
/**
 * Исключение с информацией о позиции ошибки в исходном тексте.
 */
final class ConfigException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * @param message текст ошибки с указанием позиции
     */
    ConfigException(String message) {
        super(message);
    }
}