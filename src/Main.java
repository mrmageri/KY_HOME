import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Точка входа инструмента командной строки.
 */
public final class Main {
    private static final int EXIT_OK = 0;
    private static final int EXIT_ERROR = 1;
    private static final int EXIT_USAGE = 2;

    private Main() {
    }

    /**
     * @param args параметры командной строки
     */
    public static void main(String[] args) {
        Cli cli;
        try {
            cli = Cli.parse(args);
        } catch (Cli.UsageException e) {
            System.err.println(e.getMessage());
            Cli.printUsage();
            System.exit(EXIT_USAGE);
            return;
        }
        try {
            run(cli);
        } catch (ConfigException e) {
            System.err.println(e.getMessage());
            System.exit(EXIT_ERROR);
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода: " + e.getMessage());
            System.exit(EXIT_ERROR);
        }
    }

    private static void run(Cli cli) throws ConfigException, IOException {
        String source = Files.readString(cli.input, StandardCharsets.UTF_8);
        String toml = ConfigToToml.convert(source);
        Files.writeString(cli.output, toml, StandardCharsets.UTF_8);
    }
}

/**
 * Разбор параметров командной строки.
 */
final class Cli {
    private static final int ONE = 1;
    private static final int OPTION_TOKENS = 2;

    final Path input;
    final Path output;

    private Cli(Path input, Path output) {
        this.input = input;
        this.output = output;
    }

    /**
     * @param args аргументы командной строки
     * @return разобранные параметры
     * @throws UsageException при некорректных аргументах
     */
    static Cli parse(String[] args) throws UsageException {
        String input = null;
        String output = null;
        int i = 0;
        while (i < args.length) {
            String a = args[i];
            if (isInputKey(a)) {
                input = requireValue(args, i);
                i += OPTION_TOKENS;
            } else if (isOutputKey(a)) {
                output = requireValue(args, i);
                i += OPTION_TOKENS;
            } else if (isHelpKey(a)) {
                printUsage();
                System.exit(0);
            } else {
                throw new UsageException("неизвестный аргумент: " + a);
            }
        }
        if (input == null || output == null) {
            throw new UsageException("не заданы ключи -i и -o");
        }
        return new Cli(Path.of(input), Path.of(output));
    }

    /**
     * Печатает краткую справку в stderr.
     */
    static void printUsage() {
        System.err.println("Использование: java configtotoml.Main -i <вход> -o <выход>");
    }

    private static String requireValue(String[] args, int keyIndex) throws UsageException {
        int valueIndex = keyIndex + ONE;
        if (valueIndex >= args.length) {
            throw new UsageException("не указан путь после " + args[keyIndex]);
        }
        return args[valueIndex];
    }

    private static boolean isInputKey(String a) {
        return "-i".equals(a) || "--input".equals(a);
    }

    private static boolean isOutputKey(String a) {
        return "-o".equals(a) || "--output".equals(a);
    }

    private static boolean isHelpKey(String a) {
        return "-h".equals(a) || "--help".equals(a);
    }

    /**
     * Исключение некорректных параметров командной строки.
     */
    static final class UsageException extends Exception {
        private static final long serialVersionUID = 1L;

        /**
         * @param message описание ошибки
         */
        UsageException(String message) {
            super(message);
        }
    }
}