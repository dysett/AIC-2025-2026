package zlagoda.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import java.time.LocalDate;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import zlagoda.Db;
import zlagoda.Session;

public class ReportPanel extends JPanel {
    // Панель звітів містить SQL-запити, потрібні менеджеру і касиру за вимогами.
    private final Session session;
    private final JComboBox<String> report = new JComboBox<>();
    private final JTextField from = new JTextField("2026-06-01", 10);
    private final JTextField to = new JTextField("2026-12-31", 10);
    private final JTextField employee = new JTextField(10);
    private final JTextField upc = new JTextField(12);
    private final JTextField percent = new JTextField(4);
    private final JTable table = new JTable();

    public ReportPanel(Session session) {
        super(new BorderLayout(8, 8));
        this.session = session;
        // Набір звітів залежить від ролі користувача.
        addReports();
        add(top(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        runReport();
    }

    private void addReports() {
        if (session.isManager()) {
            // Ці звіти доступні тільки менеджеру, бо містять службову інформацію про працівників.
            report.addItem("Усі працівники");
            report.addItem("Касири");
            report.addItem("Контакти працівника за прізвищем");
        }
        report.addItem("Постійні клієнти");
        report.addItem("Клієнти за відсотком знижки");
        report.addItem("Категорії");
        report.addItem("Товари");
        report.addItem("Товари у магазині");
        report.addItem("Чеки за період");
        if (session.isCashier()) {
            // Окрема касирська функція для швидкого перегляду чеків за сьогодні.
            report.addItem("Мої чеки сьогодні");
        }
        report.addItem("Товари у чеку");
        report.addItem("Сума продажів за період");
        report.addItem("Сума продажів касира");
        report.addItem("Кількість проданого товару");
        report.addItem("Інформація про себе");
        report.addItem("Звіт 3Т: продажі категорій за період");
        report.addItem("Звіт 3Т: клієнти, що купили всі категорії");
    }

    private JPanel top() {
        // Поля "від", "до", "ID/параметр", "UPC" і "відсоток" використовуються різними звітами.
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton run = new JButton("Сформувати");
        JButton print = new JButton("Друк");
        run.addActionListener(e -> runReport());
        print.addActionListener(e -> print());
        // Поля залишаються універсальними для всіх звітів:
        // один звіт використовує дату, інший - UPC, третій - ID працівника або відсоток знижки.
        // Це спрощує інтерфейс і дозволяє не створювати окрему форму під кожен SELECT.
        p.add(new JLabel("Звіт"));
        p.add(report);
        p.add(new JLabel("Від"));
        p.add(from);
        p.add(new JLabel("До"));
        p.add(to);
        p.add(new JLabel("ID/параметр"));
        p.add(employee);
        p.add(new JLabel("UPC"));
        p.add(upc);
        p.add(new JLabel("%"));
        p.add(percent);
        p.add(run);
        p.add(print);
        return p;
    }

    private void runReport() {
        try {
            String r = (String) report.getSelectedItem();
            // runReport() є центральним обробником кнопки "Сформувати".
            // Він читає вибраний пункт JComboBox і запускає відповідний SQL-запит.
            // Результат кожного SELECT одразу підставляється в JTable через Db.tableModel(...).
            switch (r) {
                case "Усі працівники" -> requireManager("""
                        -- Повний список працівників доступний тільки менеджеру.
                        SELECT * FROM Employee
                        ORDER BY empl_surname
                        """);
                case "Касири" -> requireManager("""
                        -- Відбираємо тільки працівників з посадою Cashier.
                        SELECT * FROM Employee
                        WHERE empl_role = 'Cashier'
                        ORDER BY empl_surname
                        """);
                case "Контакти працівника за прізвищем" -> requireManager("""
                        -- Пошук телефону й адреси працівника за прізвищем або його частиною.
                        SELECT id_employee, empl_surname, empl_name, empl_patronymic,
                               phone_number, city, street, zip_code
                        FROM Employee
                        WHERE lower(empl_surname) LIKE lower(?)
                        ORDER BY empl_surname, empl_name
                        """, "%" + employee.getText().trim() + "%");
                case "Постійні клієнти" -> table.setModel(Db.tableModel("""
                        -- Список клієнтів із картами лояльності.
                        SELECT * FROM Customer_Card
                        ORDER BY cust_surname
                        """));
                case "Клієнти за відсотком знижки" -> table.setModel(Db.tableModel("""
                        -- Фільтр клієнтів за конкретним відсотком знижки з карти.
                        SELECT * FROM Customer_Card
                        WHERE percent = ?
                        ORDER BY cust_surname, cust_name
                        """, percentValue()));
                case "Категорії" -> table.setModel(Db.tableModel("""
                        -- Довідник категорій товарів.
                        SELECT * FROM Category
                        ORDER BY category_name
                        """));
                case "Товари" -> table.setModel(Db.tableModel("""
                        -- Товари показуються разом з категоріями через JOIN.
                        SELECT P.id_product, P.product_name, P.manufacturer, C.category_name, P.characteristics
                        FROM Product P
                        JOIN Category C ON C.category_number = P.category_number
                        ORDER BY P.product_name
                        """));
                case "Товари у магазині" -> table.setModel(Db.tableModel("""
                        -- Звіт показує залишки, ціни і статус акційності.
                        SELECT SP.UPC, P.product_name, SP.selling_price, SP.products_number, SP.promotional_product
                        FROM Store_Product SP
                        JOIN Product P ON P.id_product = SP.id_product
                        ORDER BY SP.products_number
                        """));
                case "Чеки за період" -> table.setModel(checksByPeriod());
                case "Мої чеки сьогодні" -> table.setModel(Db.tableModel("""
                        -- Для касира це швидкий перегляд чеків, створених ним за поточний день.
                        SELECT CH.check_number, CH.print_date, CH.card_number, CH.sum_total, CH.vat
                        FROM "Check" CH
                        WHERE CH.id_employee = ?
                          AND date(CH.print_date) = date(?)
                        ORDER BY CH.print_date DESC
                        """, session.employeeId(), LocalDate.now().toString()));
                case "Товари у чеку" -> table.setModel(checkDetails());
                case "Сума продажів за період" -> table.setModel(salesTotalByPeriod());
                case "Сума продажів касира" -> table.setModel(cashierSalesTotal());
                case "Кількість проданого товару" -> table.setModel(soldProductQuantity());
                case "Інформація про себе" -> table.setModel(Db.tableModel("""
                        -- Користувач бачить повний запис про себе з таблиці Employee.
                        SELECT *
                        FROM Employee
                        WHERE id_employee = ?
                        """, session.employeeId()));
                case "Звіт 3Т: продажі категорій за період" -> table.setModel(categorySalesForCoursework());
                case "Звіт 3Т: клієнти, що купили всі категорії" -> table.setModel(customersWhoBoughtAllCategories());
                default -> throw new IllegalStateException("Unknown report");
            }
        } catch (SQLException | NumberFormatException ex) {
            // Користувач бачить помилку в діалоговому вікні, наприклад якщо параметр має неправильний формат.
            JOptionPane.showMessageDialog(this, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private javax.swing.table.DefaultTableModel checksByPeriod() throws SQLException {
        if (session.isCashier()) {
            // Касир не може ввести чужий ID і переглянути чужі чеки: ID береться з поточної сесії.
            return Db.tableModel("""
                    SELECT CH.check_number, CH.print_date, E.empl_surname || ' ' || E.empl_name AS cashier,
                           CH.card_number, CH.sum_total, CH.vat
                    FROM "Check" CH
                    JOIN Employee E ON E.id_employee = CH.id_employee
                    WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
                      AND CH.id_employee = ?
                    ORDER BY CH.print_date
                    """, from.getText().trim(), to.getText().trim(), session.employeeId());
        }
        // Менеджер може дивитися всі чеки або відфільтрувати їх за ID касира.
        return Db.tableModel("""
                SELECT CH.check_number, CH.print_date, E.empl_surname || ' ' || E.empl_name AS cashier,
                       CH.card_number, CH.sum_total, CH.vat
                FROM "Check" CH
                JOIN Employee E ON E.id_employee = CH.id_employee
                WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
                  AND (? = '' OR CH.id_employee = ?)
                ORDER BY CH.print_date
                """, from.getText().trim(), to.getText().trim(), employee.getText().trim(), employee.getText().trim());
    }

    private javax.swing.table.DefaultTableModel checkDetails() throws SQLException {
        String checkNumber = employee.getText().trim();
        if (session.isCashier()) {
            // Касир бачить деталізацію тільки тих чеків, які створив сам.
            return Db.tableModel("""
                    SELECT CH.check_number, P.product_name, S.product_number, S.selling_price,
                           S.product_number * S.selling_price AS line_total
                    FROM "Check" CH
                    JOIN Sale S ON S.check_number = CH.check_number
                    JOIN Store_Product SP ON SP.UPC = S.UPC
                    JOIN Product P ON P.id_product = SP.id_product
                    WHERE CH.id_employee = ?
                      AND (? = '' OR CH.check_number = ?)
                    ORDER BY CH.check_number, P.product_name
                    """, session.employeeId(), checkNumber, checkNumber);
        }
        // Менеджер може переглядати деталізацію будь-якого чека.
        return Db.tableModel("""
                SELECT CH.check_number, P.product_name, S.product_number, S.selling_price,
                       S.product_number * S.selling_price AS line_total
                FROM "Check" CH
                JOIN Sale S ON S.check_number = CH.check_number
                JOIN Store_Product SP ON SP.UPC = S.UPC
                JOIN Product P ON P.id_product = SP.id_product
                WHERE (? = '' OR CH.check_number = ?)
                ORDER BY CH.check_number, P.product_name
                """, checkNumber, checkNumber);
    }

    private javax.swing.table.DefaultTableModel salesTotalByPeriod() throws SQLException {
        if (session.isCashier()) {
            // Для касира загальна сума продажів рахується тільки за його власними чеками.
            return Db.tableModel("""
                    SELECT SUM(sum_total) AS total_sales, SUM(vat) AS total_vat
                    FROM "Check"
                    WHERE date(print_date) BETWEEN date(?) AND date(?)
                      AND id_employee = ?
                    """, from.getText().trim(), to.getText().trim(), session.employeeId());
        }
        return Db.tableModel("""
                SELECT SUM(sum_total) AS total_sales, SUM(vat) AS total_vat
                FROM "Check"
                WHERE date(print_date) BETWEEN date(?) AND date(?)
                """, from.getText().trim(), to.getText().trim());
    }

    private javax.swing.table.DefaultTableModel cashierSalesTotal() throws SQLException {
        if (session.isCashier()) {
            // Касир не може отримати суму продажів іншого касира.
            return Db.tableModel("""
                    SELECT E.id_employee, E.empl_surname || ' ' || E.empl_name AS cashier,
                           SUM(CH.sum_total) AS total_sales, SUM(CH.vat) AS total_vat
                    FROM "Check" CH
                    JOIN Employee E ON E.id_employee = CH.id_employee
                    WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
                      AND CH.id_employee = ?
                    GROUP BY E.id_employee, E.empl_surname, E.empl_name
                    """, from.getText().trim(), to.getText().trim(), session.employeeId());
        }
        return Db.tableModel("""
                SELECT E.id_employee, E.empl_surname || ' ' || E.empl_name AS cashier,
                       SUM(CH.sum_total) AS total_sales, SUM(CH.vat) AS total_vat
                FROM "Check" CH
                JOIN Employee E ON E.id_employee = CH.id_employee
                WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
                  AND (? = '' OR CH.id_employee = ?)
                GROUP BY E.id_employee, E.empl_surname, E.empl_name
                ORDER BY total_sales DESC
                """, from.getText().trim(), to.getText().trim(), employee.getText().trim(), employee.getText().trim());
    }

    private javax.swing.table.DefaultTableModel soldProductQuantity() throws SQLException {
        if (session.isCashier()) {
            // Для касира кількість проданого товару рахується тільки за його чеками.
            return Db.tableModel("""
                    SELECT S.UPC, P.product_name, SUM(S.product_number) AS units_sold
                    FROM Sale S
                    JOIN "Check" CH ON CH.check_number = S.check_number
                    JOIN Store_Product SP ON SP.UPC = S.UPC
                    JOIN Product P ON P.id_product = SP.id_product
                    WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
                      AND CH.id_employee = ?
                      AND (? = '' OR S.UPC = ?)
                    GROUP BY S.UPC, P.product_name
                    ORDER BY units_sold DESC
                    """, from.getText().trim(), to.getText().trim(), session.employeeId(), upc.getText().trim(), upc.getText().trim());
        }
        return Db.tableModel("""
                SELECT S.UPC, P.product_name, SUM(S.product_number) AS units_sold
                FROM Sale S
                JOIN "Check" CH ON CH.check_number = S.check_number
                JOIN Store_Product SP ON SP.UPC = S.UPC
                JOIN Product P ON P.id_product = SP.id_product
                WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
                  AND (? = '' OR S.UPC = ?)
                GROUP BY S.UPC, P.product_name
                ORDER BY units_sold DESC
                """, from.getText().trim(), to.getText().trim(), upc.getText().trim(), upc.getText().trim());
    }

    private javax.swing.table.DefaultTableModel categorySalesForCoursework() throws SQLException {
        // Запит для індивідуального звіту з 3 триместру.
        // Параметричність: межі періоду не зашиті в SQL, а беруться з полів "Від" і "До".
        // Після натискання "Сформувати" ці значення передаються у два знаки питання WHERE date(...) BETWEEN date(?) AND date(?).
        // Багатотабличність: у запиті використано 5 таблиць:
        // Check - дата продажу і номер чека;
        // Sale - продані позиції, кількість і ціна;
        // Store_Product - зв'язок UPC з товаром;
        // Product - номенклатура товарів;
        // Category - назва категорії для групування.
        // Групування: GROUP BY C.category_number, C.category_name збирає всі продажі однієї категорії в один рядок.
        return Db.tableModel("""
                -- Параметричний багатотабличний запит із групуванням.
                -- Показує до 5 категорій із найбільшою сумою продажів за вибраний період.
                SELECT C.category_name AS category,
                       -- COUNT(DISTINCT ...) рахує кількість різних чеків, у яких були товари категорії.
                       COUNT(DISTINCT CH.check_number) AS checks_count,
                       -- SUM(S.product_number) показує загальну кількість проданих одиниць у категорії.
                       SUM(S.product_number) AS units_sold,
                       -- Сума рядка продажу = кількість * ціна продажу, ROUND залишає 2 знаки після коми.
                       ROUND(SUM(S.product_number * S.selling_price), 2) AS sales_sum,
                       -- AVG дає середню ціну продажу позицій цієї категорії.
                       ROUND(AVG(S.selling_price), 2) AS avg_price
                FROM "Check" CH
                JOIN Sale S ON S.check_number = CH.check_number
                JOIN Store_Product SP ON SP.UPC = S.UPC
                JOIN Product P ON P.id_product = SP.id_product
                JOIN Category C ON C.category_number = P.category_number
                WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
                GROUP BY C.category_number, C.category_name
                ORDER BY sales_sum DESC, C.category_name
                LIMIT 5
                """, from.getText().trim(), to.getText().trim());
    }

    private javax.swing.table.DefaultTableModel customersWhoBoughtAllCategories() throws SQLException {
        // Запит для індивідуального звіту з подвійним запереченням.
        // Логіка читається так:
        // "вибрати клієнта, для якого НЕ ІСНУЄ категорії,
        //  для якої НЕ ІСНУЄ покупки цього клієнта".
        // У реляційному сенсі це реалізує умову "клієнт купив товари з усіх категорій".
        // Багатотабличність: перевірка покупки проходить через Check, Sale, Store_Product і Product,
        // а список усіх категорій береться з Category.
        return Db.tableModel("""
                -- Багатотабличний запит із подвійним запереченням.
                -- Знаходить постійних клієнтів, які купили хоча б один товар з кожної категорії.
                SELECT CC.card_number,
                       CC.cust_surname || ' ' || CC.cust_name AS customer,
                       CC.phone_number,
                       CC.percent
                FROM Customer_Card CC
                WHERE NOT EXISTS (
                    -- Зовнішній підзапит перебирає всі категорії з довідника Category.
                    SELECT 1
                    FROM Category C
                    WHERE NOT EXISTS (
                        -- Внутрішній підзапит шукає хоча б одну покупку поточного клієнта CC
                        -- у поточній категорії C. Якщо такої покупки немає, внутрішній NOT EXISTS істинний.
                        SELECT 1
                        FROM "Check" CH
                        JOIN Sale S ON S.check_number = CH.check_number
                        JOIN Store_Product SP ON SP.UPC = S.UPC
                        JOIN Product P ON P.id_product = SP.id_product
                        WHERE CH.card_number = CC.card_number
                          AND P.category_number = C.category_number
                    )
                )
                ORDER BY CC.cust_surname, CC.cust_name
                LIMIT 5
                """);
    }

    private int percentValue() {
        // Для фільтра клієнтів поле відсотка має бути заповнене цілим числом.
        return Integer.parseInt(percent.getText().trim());
    }

    private void requireManager(String sql, Object... params) throws SQLException {
        // Додаткова перевірка ролі захищає менеджерські звіти.
        if (!session.isManager()) {
            throw new SQLException("Звіт доступний тільки менеджеру");
        }
        table.setModel(Db.tableModel(sql, params));
    }

    private void print() {
        try {
            // Друк звіту виконується з поточного JTable.
            table.print();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Print error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
