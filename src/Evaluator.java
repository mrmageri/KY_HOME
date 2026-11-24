import java.math.BigInteger;
import java.util.List;

/**
 * Вычислитель константных выражений.
 */
final class Evaluator {
    private static final int UNARY_ARITY = 1;
    private static final int BINARY_ARITY = 2;
    private static final long ZERO = 0L;
    private static final double ZERO_D = 0.0;
    private static final int MIN_UNICODE = 0;
    private static final int MAX_UNICODE = 0x10FFFF;
    private static final long MAX_POW_EXPONENT = 10_000L;

    private Evaluator() {
    }

    /**
     * Вычисляет выражение {@code op arg1 ...}.
     *
     * @param op    имя операции
     * @param args  аргументы
     * @param where позиция для сообщений
     * @return значение
     * @throws ConfigException при ошибке в аргументах или вычислении
     */
    static Value evaluate(String op, List<Value> args, Token where) throws ConfigException {
        switch (op) {
            case "+": case "-": case "*": case "/":
                return arithmetic(op, args, where);
            case "chr":
                return chr(args, where);
            case "pow":
                return pow(args, where);
            default:
                throw err("неизвестная операция '" + op + "'", where);
        }
    }

    private static Value arithmetic(String op, List<Value> args, Token where)
            throws ConfigException {
        checkArity(args, BINARY_ARITY, op, where);
        VNum a = asNum(args.get(0), op, where);
        VNum b = asNum(args.get(1), op, where);
        if (a.isInt && b.isInt) {
            return integerArithmetic(op, a.lval, b.lval, where);
        }
        return realArithmetic(op, a.dval, b.dval, where);
    }

    private static Value integerArithmetic(String op, long a, long b, Token where)
            throws ConfigException {
        switch (op) {
            case "+": return VNum.ofLong(safeAdd(a, b, where));
            case "-": return VNum.ofLong(safeSub(a, b, where));
            case "*": return VNum.ofLong(safeMul(a, b, where));
            default:  return integerDivision(a, b, where);
        }
    }

    private static Value integerDivision(long a, long b, Token where) throws ConfigException {
        if (b == ZERO) {
            throw err("деление на ноль", where);
        }
        if (a % b == ZERO) {
            return VNum.ofLong(a / b);
        }
        return VNum.ofDouble((double) a / (double) b);
    }

    private static Value realArithmetic(String op, double a, double b, Token where)
            throws ConfigException {
        switch (op) {
            case "+": return VNum.ofDouble(a + b);
            case "-": return VNum.ofDouble(a - b);
            case "*": return VNum.ofDouble(a * b);
            default:  return realDivision(a, b, where);
        }
    }

    private static Value realDivision(double a, double b, Token where) throws ConfigException {
        if (b == ZERO_D) {
            throw err("деление на ноль", where);
        }
        return VNum.ofDouble(a / b);
    }

    private static Value chr(List<Value> args, Token where) throws ConfigException {
        checkArity(args, UNARY_ARITY, "chr", where);
        VNum num = asNum(args.get(0), "chr", where);
        long code = asCharCode(num, where);
        if (code < MIN_UNICODE || code > MAX_UNICODE) {
            throw err("код символа вне диапазона: " + code, where);
        }
        return new VStr(new String(Character.toChars((int) code)));
    }

    private static long asCharCode(VNum num, Token where) throws ConfigException {
        if (num.isInt) {
            return num.lval;
        }
        if (num.hasWholeValue()) {
            return (long) num.dval;
        }
        throw err("chr ожидает целое число", where);
    }

    private static Value pow(List<Value> args, Token where) throws ConfigException {
        checkArity(args, BINARY_ARITY, "pow", where);
        VNum a = asNum(args.get(0), "pow", where);
        VNum b = asNum(args.get(1), "pow", where);
        if (a.isInt && b.isInt && b.lval >= ZERO) {
            return integerPow(a.lval, b.lval, where);
        }
        return realPow(a.dval, b.dval, where);
    }

    private static Value integerPow(long base, long exp, Token where) throws ConfigException {
        if (exp > MAX_POW_EXPONENT) {
            throw err("слишком большая степень: " + exp, where);
        }
        BigInteger result = BigInteger.valueOf(base).pow((int) exp);
        if (result.bitLength() < Long.SIZE) {
            return VNum.ofLong(result.longValue());
        }
        double value = result.doubleValue();
        if (Double.isInfinite(value)) {
            throw err("переполнение при возведении в степень", where);
        }
        return VNum.ofDouble(value);
    }

    private static Value realPow(double base, double exp, Token where) throws ConfigException {
        double result = Math.pow(base, exp);
        if (Double.isNaN(result)) {
            throw err("некорректный результат pow", where);
        }
        if (Double.isInfinite(result)) {
            throw err("переполнение при возведении в степень", where);
        }
        return VNum.ofDouble(result);
    }

    private static VNum asNum(Value v, String op, Token where) throws ConfigException {
        if (!(v instanceof VNum)) {
            throw err("операнд операции '" + op + "' должен быть числом", where);
        }
        return (VNum) v;
    }

    private static void checkArity(List<Value> args, int expected, String op, Token where)
            throws ConfigException {
        if (args.size() != expected) {
            throw err("операция '" + op + "' ожидает " + expected
                    + " операнда(ов), получено " + args.size(), where);
        }
    }

    private static long safeAdd(long x, long y, Token where) throws ConfigException {
        try {
            return Math.addExact(x, y);
        } catch (ArithmeticException e) {
            throw err("переполнение при сложении", where);
        }
    }

    private static long safeSub(long x, long y, Token where) throws ConfigException {
        try {
            return Math.subtractExact(x, y);
        } catch (ArithmeticException e) {
            throw err("переполнение при вычитании", where);
        }
    }

    private static long safeMul(long x, long y, Token where) throws ConfigException {
        try {
            return Math.multiplyExact(x, y);
        } catch (ArithmeticException e) {
            throw err("переполнение при умножении", where);
        }
    }

    private static ConfigException err(String message, Token where) {
        return new ConfigException(
                "Ошибка (строка " + where.line + ", столбец " + where.col + "): " + message);
    }
}