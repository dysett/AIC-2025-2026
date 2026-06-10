package zlagoda;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;

public final class Db {
    private static final String URL = "jdbc:sqlite:data/zlagoda.db";

    private Db() {
    }

    public static Connection connect() throws SQLException {
        // DriverManager відкриває фізичне підключення до SQLite-файлу data/zlagoda.db.
        // Усі класи застосунку використовують цей метод, щоб підключення налаштовувались однаково.
        Connection con = DriverManager.getConnection(URL);
        try (Statement st = con.createStatement()) {
            // У SQLite зовнішні ключі треба вмикати для кожного нового підключення.
            // Без цього SQLite прийме FOREIGN KEY у схемі, але не буде перевіряти їх під час INSERT/UPDATE/DELETE.
            st.execute("PRAGMA foreign_keys = ON");
        }
        return con;
    }

    public static void initialize() {
        try {
            // Папка data зберігає локальний файл бази даних.
            // Її створення тут дозволяє запускати програму одразу після розпакування проєкту.
            Files.createDirectories(Path.of("data"));

            // Явне завантаження JDBC-драйвера потрібне, щоб DriverManager знайшов sqlite-jdbc.
            Class.forName("org.sqlite.JDBC");
            try (Connection con = connect()) {
                // Схема і тестові дані створюються автоматично, щоб проєкт запускався одразу.
                createSchema(con);
                seedData(con);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Database initialization failed", ex);
        }
    }

    private static void createSchema(Connection con) throws SQLException {
        // Таблиці відповідають реляційній моделі ZLAGODA.
        // Додаткова таблиця App_User потрібна для аутентифікації та ролей.
        String[] sql = {
            // Employee зберігає працівників супермаркету: менеджерів і касирів.
            // Поле empl_role обмежене двома значеннями, щоб не було випадкових ролей.
            """
            CREATE TABLE IF NOT EXISTS Employee (
                id_employee TEXT PRIMARY KEY,
                empl_surname TEXT NOT NULL,
                empl_name TEXT NOT NULL,
                empl_patronymic TEXT,
                empl_role TEXT NOT NULL CHECK (empl_role IN ('Manager','Cashier')),
                salary NUMERIC NOT NULL CHECK (salary >= 0),
                date_of_birth TEXT NOT NULL,
                date_of_start TEXT NOT NULL,
                phone_number TEXT NOT NULL CHECK (length(phone_number) <= 13),
                city TEXT NOT NULL,
                street TEXT NOT NULL,
                zip_code TEXT NOT NULL
            )
            """,
            // Customer_Card містить дані постійних клієнтів і їхній відсоток знижки.
            // Адреса клієнта необов'язкова, тому city/street/zip_code можуть бути NULL.
            """
            CREATE TABLE IF NOT EXISTS Customer_Card (
                card_number TEXT PRIMARY KEY,
                cust_surname TEXT NOT NULL,
                cust_name TEXT NOT NULL,
                cust_patronymic TEXT,
                phone_number TEXT NOT NULL CHECK (length(phone_number) <= 13),
                city TEXT,
                street TEXT,
                zip_code TEXT,
                percent INTEGER NOT NULL CHECK (percent >= 0)
            )
            """,
            // Category є довідником категорій товарів.
            """
            CREATE TABLE IF NOT EXISTS Category (
                category_number INTEGER PRIMARY KEY,
                category_name TEXT NOT NULL UNIQUE
            )
            """,
            // Product описує тип товару і посилається на категорію.
            // Один товар належить тільки одній категорії.
            """
            CREATE TABLE IF NOT EXISTS Product (
                id_product INTEGER PRIMARY KEY,
                category_number INTEGER NOT NULL,
                product_name TEXT NOT NULL,
                manufacturer TEXT NOT NULL DEFAULT 'Unknown',
                characteristics TEXT NOT NULL,
                FOREIGN KEY (category_number) REFERENCES Category(category_number)
                    ON UPDATE CASCADE ON DELETE NO ACTION
            )
            """,
            // Store_Product описує конкретний товар у магазині з UPC, ціною, залишком і ознакою акції.
            // UPC_prom посилається на звичайний товар, якщо цей запис є акційним варіантом.
            """
            CREATE TABLE IF NOT EXISTS Store_Product (
                UPC TEXT PRIMARY KEY,
                UPC_prom TEXT,
                id_product INTEGER NOT NULL,
                selling_price NUMERIC NOT NULL CHECK (selling_price >= 0),
                products_number INTEGER NOT NULL CHECK (products_number >= 0),
                promotional_product INTEGER NOT NULL CHECK (promotional_product IN (0,1)),
                FOREIGN KEY (UPC_prom) REFERENCES Store_Product(UPC)
                    ON UPDATE CASCADE ON DELETE SET NULL,
                FOREIGN KEY (id_product) REFERENCES Product(id_product)
                    ON UPDATE CASCADE ON DELETE NO ACTION
            )
            """,
            // Check зберігає заголовок чека: касир, карта клієнта, дата, загальна сума і ПДВ.
            // Назва таблиці взята в лапки, бо CHECK є службовим словом у SQL.
            """
            CREATE TABLE IF NOT EXISTS "Check" (
                check_number TEXT PRIMARY KEY,
                id_employee TEXT NOT NULL,
                card_number TEXT,
                print_date TEXT NOT NULL,
                sum_total NUMERIC NOT NULL CHECK (sum_total >= 0),
                vat NUMERIC NOT NULL CHECK (vat >= 0),
                FOREIGN KEY (id_employee) REFERENCES Employee(id_employee)
                    ON UPDATE CASCADE ON DELETE NO ACTION,
                FOREIGN KEY (card_number) REFERENCES Customer_Card(card_number)
                    ON UPDATE CASCADE ON DELETE NO ACTION
            )
            """,
            // Sale є таблицею-зв'язком між чеком і товаром у магазині.
            // Тут фіксується кількість і ціна продажу саме на момент покупки.
            """
            CREATE TABLE IF NOT EXISTS Sale (
                UPC TEXT NOT NULL,
                check_number TEXT NOT NULL,
                product_number INTEGER NOT NULL CHECK (product_number > 0),
                selling_price NUMERIC NOT NULL CHECK (selling_price >= 0),
                PRIMARY KEY (UPC, check_number),
                FOREIGN KEY (UPC) REFERENCES Store_Product(UPC)
                    ON UPDATE CASCADE ON DELETE NO ACTION,
                FOREIGN KEY (check_number) REFERENCES "Check"(check_number)
                    ON UPDATE CASCADE ON DELETE CASCADE
            )
            """,
            // App_User не входить у предметну модель супермаркету, але потрібна для входу в систему.
            // У ній зберігається роль користувача і прив'язка до працівника.
            """
            CREATE TABLE IF NOT EXISTS App_User (
                user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                role TEXT NOT NULL CHECK (role IN ('MANAGER','CASHIER')),
                id_employee TEXT,
                FOREIGN KEY (id_employee) REFERENCES Employee(id_employee)
                    ON UPDATE CASCADE ON DELETE SET NULL
            )
            """
        };
        try (Statement st = con.createStatement()) {
            for (String s : sql) {
                st.execute(s);
            }
        }
        migrateSchema(con);
    }

    private static void migrateSchema(Connection con) throws SQLException {
        // Міграція потрібна, якщо база вже була створена старішою версією програми.
        if (!hasColumn(con, "Product", "manufacturer")) {
            try (Statement st = con.createStatement()) {
                st.execute("ALTER TABLE Product ADD COLUMN manufacturer TEXT NOT NULL DEFAULT 'Unknown'");
            }
        }
    }

    private static boolean hasColumn(Connection con, String table, String column) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void seedData(Connection con) throws SQLException {
        if (count(con, "Employee") > 0) {
            // Якщо дані вже є, повторно їх не додаємо, щоб не створювати дублікати.
            return;
        }
        con.setAutoCommit(false);
        try {
            // Початкові записи потрібні для демонстрації роботи системи без ручного введення.
            // Працівники створюються першими, бо чеки та користувачі посилаються на Employee.
            execute(con, "INSERT INTO Employee VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    "E000000001", "Klymenko", "Iryna", "Andriivna", "Manager", 28500, "1990-01-27", "2018-06-01", "+380931112202", "Lviv", "Naukova 4", "79060");
            execute(con, "INSERT INTO Employee VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    "E000000002", "Danyliuk", "Oksana", "Ihorivna", "Cashier", 18900, "1997-05-18", "2022-02-07", "+380501112200", "Lviv", "Shevchenka 12", "79000");
            execute(con, "INSERT INTO Employee VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    "E000000003", "Mazur", "Roman", "Petrovych", "Cashier", 19300, "1995-11-03", "2021-09-15", "+380671112201", "Lviv", "Franko 21", "79005");

            // Карти клієнтів використовуються під час продажу для застосування знижки.
            execute(con, "INSERT INTO Customer_Card VALUES (?,?,?,?,?,?,?,?,?)",
                    "0000000000001", "Koval", "Maria", "Stepanivna", "+380501010111", "Lviv", "Lychakivska 7", "79010", 5);
            execute(con, "INSERT INTO Customer_Card VALUES (?,?,?,?,?,?,?,?,?)",
                    "0000000000002", "Honchar", "Pavlo", null, "+380671010222", "Lviv", "Stryiska 30", "79026", 3);
            execute(con, "INSERT INTO Customer_Card VALUES (?,?,?,?,?,?,?,?,?)",
                    "0000000000003", "Shevchenko", "Olha", "Mykhailivna", "+380931010333", "Lviv", "Bandery 18", "79013", 7);

            // Категорії додаються до товарів, тому вони мають існувати до Product.
            execute(con, "INSERT INTO Category VALUES (?,?)", 1, "Dairy");
            execute(con, "INSERT INTO Category VALUES (?,?)", 2, "Bakery");
            execute(con, "INSERT INTO Category VALUES (?,?)", 3, "Drinks");
            execute(con, "INSERT INTO Category VALUES (?,?)", 4, "Fruit");

            // Product описує номенклатуру товарів без інформації про залишок у магазині.
            execute(con, "INSERT INTO Product (id_product, category_number, product_name, manufacturer, characteristics) VALUES (?,?,?,?,?)", 1, 1, "Milk 2.5 percent", "Halychyna", "Bottle 900 ml");
            execute(con, "INSERT INTO Product (id_product, category_number, product_name, manufacturer, characteristics) VALUES (?,?,?,?,?)", 2, 1, "Cottage Cheese", "Molokiya", "Pack 350 g");
            execute(con, "INSERT INTO Product (id_product, category_number, product_name, manufacturer, characteristics) VALUES (?,?,?,?,?)", 3, 2, "Wheat Bread", "Lviv Bakery", "Loaf 550 g");
            execute(con, "INSERT INTO Product (id_product, category_number, product_name, manufacturer, characteristics) VALUES (?,?,?,?,?)", 4, 2, "Baguette", "Lviv Bakery", "Fresh bakery 250 g");
            execute(con, "INSERT INTO Product (id_product, category_number, product_name, manufacturer, characteristics) VALUES (?,?,?,?,?)", 5, 3, "Apple Juice", "Sandora", "Carton 1 l");
            execute(con, "INSERT INTO Product (id_product, category_number, product_name, manufacturer, characteristics) VALUES (?,?,?,?,?)", 6, 3, "Mineral Water", "Morshynska", "Bottle 1.5 l");
            execute(con, "INSERT INTO Product (id_product, category_number, product_name, manufacturer, characteristics) VALUES (?,?,?,?,?)", 7, 4, "Apples", "Local Farm", "Golden, 1 kg");
            execute(con, "INSERT INTO Product (id_product, category_number, product_name, manufacturer, characteristics) VALUES (?,?,?,?,?)", 8, 4, "Bananas", "Tropical Import", "Fresh, 1 kg");

            // Store_Product додає до товарів UPC, ціну продажу, кількість і статус акційності.
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111001", null, 1, 41.90, 120, 0);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111101", "482111111001", 1, 33.52, 18, 1);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111002", null, 2, 48.40, 70, 0);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111003", null, 3, 29.90, 85, 0);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111004", null, 4, 24.70, 52, 0);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111104", "482111111004", 4, 19.76, 12, 1);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111005", null, 5, 57.60, 42, 0);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111006", null, 6, 17.50, 130, 0);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111007", null, 7, 38.20, 74, 0);
            execute(con, "INSERT INTO Store_Product VALUES (?,?,?,?,?,?)", "482111111008", null, 8, 46.10, 68, 0);

            // Чеки створюються після працівників і карт клієнтів, бо мають зовнішні ключі.
            execute(con, "INSERT INTO \"Check\" VALUES (?,?,?,?,?,?)", "CHK0000001", "E000000002", "0000000000001", "2026-06-01 09:12:00", 167.60, 33.52);
            execute(con, "INSERT INTO \"Check\" VALUES (?,?,?,?,?,?)", "CHK0000002", "E000000003", "0000000000002", "2026-06-02 17:35:00", 181.50, 36.30);
            execute(con, "INSERT INTO \"Check\" VALUES (?,?,?,?,?,?)", "CHK0000003", "E000000002", null, "2026-06-03 12:20:00", 98.54, 19.71);

            // Sale деталізує кожен чек: який UPC купили, скільки одиниць і за якою ціною.
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111001", "CHK0000001", 2, 41.90);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111003", "CHK0000001", 1, 29.90);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111007", "CHK0000001", 1, 38.20);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111006", "CHK0000001", 1, 17.50);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111002", "CHK0000002", 1, 48.40);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111004", "CHK0000002", 2, 24.70);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111005", "CHK0000002", 1, 57.60);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111008", "CHK0000002", 1, 46.10);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111101", "CHK0000003", 1, 33.52);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111104", "CHK0000003", 1, 19.76);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111006", "CHK0000003", 1, 17.50);
            execute(con, "INSERT INTO Sale VALUES (?,?,?,?)", "482111111007", "CHK0000003", 1, 38.20);

            // Демонстраційні користувачі дозволяють одразу перевірити авторизацію за ролями.
            addUser(con, "manager", "Manager123!".toCharArray(), "MANAGER", "E000000001");
            addUser(con, "cashier", "Cashier123!".toCharArray(), "CASHIER", "E000000002");

            // Усі тестові записи додаються в одній транзакції.
            con.commit();
        } catch (SQLException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(true);
        }
    }

    private static void addUser(Connection con, String username, char[] password, String role, String employeeId) throws SQLException {
        // У базі зберігається не пароль, а salt і PBKDF2-хеш.
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(password, salt);
        execute(con, "INSERT INTO App_User (username, password_hash, password_salt, role, id_employee) VALUES (?,?,?,?,?)",
                username, hash, salt, role, employeeId);
    }

    private static int count(Connection con, String table) throws SQLException {
        // Метод використовується для перевірки, чи вже була заповнена база.
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static int execute(String sql, Object... params) throws SQLException {
        // Універсальний метод для INSERT/UPDATE/DELETE.
        // Він сам відкриває підключення, виконує команду і закриває підключення через try-with-resources.
        try (Connection con = connect()) {
            return execute(con, sql, params);
        }
    }

    public static int execute(Connection con, String sql, Object... params) throws SQLException {
        // Цей варіант приймає вже відкрите підключення.
        // Він використовується в транзакціях, наприклад під час створення чека,
        // коли кілька INSERT/UPDATE мають або виконатися всі разом, або всі відкотитися.
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    public static DefaultTableModel tableModel(String sql, Object... params) throws SQLException {
        // Метод виконує SELECT і перетворює ResultSet у DefaultTableModel для JTable.
        // Саме через цей метод більшість звітів переходить від SQL-запиту до таблиці на екрані.
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            // Значення для знаків питання у SQL передаються окремо від тексту запиту.
            // Наприклад, дати "Від" і "До" у звіті стають параметрами PreparedStatement.
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                // ResultSetMetaData дозволяє дізнатися назви колонок без ручного дублювання.
                // Тому заголовки JTable автоматично відповідають SELECT-виразам або псевдонімам AS.
                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();
                String[] names = new String[columns];
                for (int i = 0; i < columns; i++) {
                    names[i] = md.getColumnLabel(i + 1);
                }
                DefaultTableModel model = new DefaultTableModel(names, 0) {
                    @Override
                    public boolean isCellEditable(int row, int column) {
                        // Звітні таблиці тільки відображають результат запиту.
                        // Редагування результату прямо в JTable вимкнене, щоб не створювати ілюзію зміни БД.
                        return false;
                    }
                };
                while (rs.next()) {
                    // Кожен рядок ResultSet копіюється в Object[].
                    // Після цього рядок додається до моделі, і Swing сам перемальовує JTable.
                    Object[] row = new Object[columns];
                    for (int i = 0; i < columns; i++) {
                        Object value = rs.getObject(i + 1);
                        if (value instanceof BigDecimal bd) {
                            // Грошові та числові значення показуються без наукового формату.
                            value = bd.toPlainString();
                        }
                        row[i] = value;
                    }
                    model.addRow(row);
                }
                return model;
            }
        }
    }

    public static Object scalar(String sql, Object... params) throws SQLException {
        // scalar повертає одне значення з першої колонки першого рядка.
        // Він зручний для перевірок COUNT(*), SUM(...), пошуку ціни або іншого одиничного результату.
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1) : null;
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        // Параметри підставляються без конкатенації рядків, щоб уникнути SQL injection.
        // Нумерація параметрів у JDBC починається з 1, тому використовується i + 1.
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    public static List<String> categories() throws SQLException {
        // Допоміжний метод для отримання списку категорій, якщо він потрібен UI.
        List<String> result = new ArrayList<>();
        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement("SELECT category_name FROM Category ORDER BY category_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }
}
