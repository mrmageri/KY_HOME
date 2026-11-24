public final class ConfigToTomlTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        runBasics();
        runNumbers();
        runStrings();
        runNesting();
        runConstants();
        runExamples();
        runErrors();
        report();
    }

    private static void runBasics() {
        eq("пустой файл", "", "");
        eq("пустой словарь", "$[ ]", "");
        eq("простой словарь", "$[ a : 1, b : q(text) ]", "a = 1\nb = \"text\"\n");
        eq("висячая запятая", "$[ a : 1, ]", "a = 1\n");
        eq("комментарии",
                "\\ заголовок\n$[ a : 1 \\ в строке\n, b : 2 ]\n\\ конец",
                "a = 1\nb = 2\n");
    }

    private static void runNumbers() {
        eq("числа",
                "$[ n_one : -1, n_two : 1.5, n_three : .5, n_four : 1.,"
                        + " n_five : 1e3, n_six : -2.5E-3, n_seven : 007, n_eight : -0.0 ]",
                "n_one = -1\nn_two = 1.5\nn_three = 0.5\nn_four = 1.0\nn_five = 1e3\n"
                        + "n_six = -2.5e-3\nn_seven = 7\nn_eight = -0.0\n");
    }

    private static void runStrings() {
        eq("экранирование",
                "$[ s : q(He said \"hi\" C:\\path) ]",
                "s = \"He said \\\"hi\\\" C:\\\\path\"\n");
        eq("юникод", "$[ s : q(Привет) ]", "s = \"Привет\"\n");
    }

    private static void runNesting() {
        eq("вложенные словари",
                "$[ server : $[ port : 8080,"
                        + " tls : $[ cert : q(/c.pem) ] ], name : q(app) ]",
                "name = \"app\"\n\n[server]\nport = 8080\n"
                        + "\n[server.tls]\ncert = \"/c.pem\"\n");
        eq("словарь в константе",
                "var d = $[ x : 1 ]\n$[ t : d ]",
                "[t]\nx = 1\n");
    }

    private static void runConstants() {
        eq("var + ссылка", "var x = 41\n$[ answer : {+ x 1} ]", "answer = 42\n");
        eq("все операции",
                "var a = 10\nvar b = 3\n$[\n"
                        + "  s : {+ a b},\n  d : {- a b},\n  m : {* a b},\n"
                        + "  q_one : {/ a 5},\n  q_two : {/ a b},\n  c : {chr 65},\n"
                        + "  p : {pow 2 10},\n  n : {* {+ a b} 2},\n  neg : {- 0 5}\n]",
                "s = 13\nd = 7\nm = 30\nq_one = 2\nq_two = 3.3333333333333335\n"
                        + "c = \"A\"\np = 1024\nn = 26\nneg = -5\n");
        eq("chr юникод",
                "var code = {+ 1000 46}\n$[ ch : {chr code} ]",
                "ch = \"Ж\"\n");
    }

    private static void runExamples() {
        eq("веб-сервер", WEB_CFG, WEB_TOML);
        eq("игровой уровень", GAME_CFG, GAME_TOML);
        eq("эксперимент", SCIENCE_CFG, SCIENCE_TOML);
    }

    private static void runErrors() {
        fails("незакрытый словарь", "$[ a : 1", "ожидалось");
        fails("нет '$['", "a : 1", "ожидалось");
        fails("недопустимый символ", "$[ a : # ]", "недопустимый символ");
        fails("неизвестная константа", "$[ a : foo ]", "неизвестная константа");
        fails("дубликат ключа", "$[ a : 1, a : 2 ]", "дубликат ключа");
        fails("дубликат константы", "var a = 1\nvar a = 2", "уже объявлена");
        fails("деление на ноль", "$[ a : {/ 1 0} ]", "деление на ноль");
        fails("незакрытая строка", "$[ a : q(abc ]", "незакрытая строка");
        fails("неверная арность", "$[ a : {+ 1} ]", "ожидает 2 операнда");
        fails("нечисловой операнд", "$[ a : {+ q(x) 1} ]", "должен быть числом");
        fails("неизвестная операция", "$[ a : {mod 1 2} ]", "неизвестная операция");
        fails("chr вне диапазона", "$[ a : {chr -1} ]", "вне диапазона");
        fails("словарь как операнд",
                "$[ a : {+ $[ x : 1 ] 1} ]", "не может быть операндом");
    }

    private static void report() {
        System.out.println("Пройдено: " + passed + ", провалено: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void eq(String name, String input, String expected) {
        try {
            String actual = ConfigToToml.convert(input);
            if (expected.equals(actual)) {
                passed++;
            } else {
                failed++;
                System.out.println("FAIL: " + name + "\n--- ожидалось ---\n"
                        + expected + "--- получено ---\n" + actual);
            }
        } catch (ConfigException e) {
            failed++;
            System.out.println("FAIL: " + name
                    + " — неожиданное исключение: " + e.getMessage());
        }
    }

    private static void fails(String name, String input, String expectedPart) {
        try {
            String actual = ConfigToToml.convert(input);
            failed++;
            System.out.println("FAIL: " + name
                    + " — ожидалась ошибка, получено:\n" + actual);
        } catch (ConfigException e) {
            if (e.getMessage().contains(expectedPart)) {
                passed++;
            } else {
                failed++;
                System.out.println("FAIL: " + name + " — сообщение: " + e.getMessage());
            }
        }
    }

    private static final String WEB_CFG = """
            \\ Конфигурация веб-сервера
            var default_port = 8080
            var timeout_sec = 30
            $[
              app : q(gateway),
              port : default_port,
              timeout : timeout_sec,
              host : q(0.0.0.0),
              tls : $[
                enabled : 1,
                cert : q(/etc/ssl/server.crt),
                key : q(/etc/ssl/server.key)
              ],
              logging : $[
                level : q(info),
                file : q(/var/log/gateway.log),
                rotate_days : 7
              ],
              limits : $[
                max_conn : {* 2 5000},
                max_body_mb : 64
              ]
            ]
            """;

    private static final String WEB_TOML = """
            app = "gateway"
            port = 8080
            timeout = 30
            host = "0.0.0.0"

            [tls]
            enabled = 1
            cert = "/etc/ssl/server.crt"
            key = "/etc/ssl/server.key"

            [logging]
            level = "info"
            file = "/var/log/gateway.log"
            rotate_days = 7

            [limits]
            max_conn = 10000
            max_body_mb = 64
            """;

    private static final String GAME_CFG = """
            \\ Описание уровня игры
            var tile = 32
            var width = 40
            var height = 25
            $[
              level : q(forest_01),
              music : q(forest_theme.ogg),
              size : $[
                width_px : {* tile width},
                height_px : {* tile height}
              ],
              player : $[
                name : q(ranger),
                speed : 4.5,
                start : $[
                  x : 3,
                  y : 17
                ]
              ],
              enemies : $[
                count : 12,
                boss : $[
                  hp : {pow 2 8},
                  damage : {/ 100 8}
                ]
              ]
            ]
            """;

    private static final String GAME_TOML = """
            level = "forest_01"
            music = "forest_theme.ogg"

            [size]
            width_px = 1280
            height_px = 800

            [player]
            name = "ranger"
            speed = 4.5

            [player.start]
            x = 3
            y = 17

            [enemies]
            count = 12

            [enemies.boss]
            hp = 256
            damage = 12.5
            """;

    private static final String SCIENCE_CFG = """
            \\ Параметры численного эксперимента
            var points = {pow 2 10}
            var dt = 1e-3
            $[
              experiment : q(pendulum),
              notes : q(Проверка сохранения энергии),
              solver : $[
                method : q(runge_kutta_4),
                steps : points,
                step_size : dt,
                tolerance : 1e-9
              ],
              pendulum : $[
                length_m : 1.25,
                gravity : 9.81,
                initial_angle_rad : 0.35
              ],
              output : $[
                dir : q(results/pendulum),
                format : q(csv),
                every_n_steps : 16
              ]
            ]
            """;

    private static final String SCIENCE_TOML = """
            experiment = "pendulum"
            notes = "Проверка сохранения энергии"

            [solver]
            method = "runge_kutta_4"
            steps = 1024
            step_size = 1e-3
            tolerance = 1e-9

            [pendulum]
            length_m = 1.25
            gravity = 9.81
            initial_angle_rad = 0.35

            [output]
            dir = "results/pendulum"
            format = "csv"
            every_n_steps = 16
            """;
}