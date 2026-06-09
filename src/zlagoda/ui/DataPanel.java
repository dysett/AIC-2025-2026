package zlagoda.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import zlagoda.Db;

public class DataPanel extends JPanel {
    // Універсальна CRUD-панель: одна логіка використовується для різних таблиць.
    private final String table;
    private final String pk;
    private final String orderBy;
    private final String[] columns;
    private final boolean allowDelete;
    private final JTable grid = new JTable();
    private final Map<String, JTextField> fields = new LinkedHashMap<>();

    public DataPanel(String table, String pk, String orderBy, String[] columns, boolean allowDelete) {
        super(new BorderLayout(8, 8));
        this.table = table;
        this.pk = pk;
        this.orderBy = orderBy;
        this.columns = columns;
        this.allowDelete = allowDelete;
        add(new JScrollPane(grid), BorderLayout.CENTER);
        add(form(), BorderLayout.EAST);
        refresh();
    }

    public static DataPanel employees() {
        // Панель працівників доступна менеджеру для ведення персоналу.
        return new DataPanel("Employee", "id_employee", "empl_surname",
                new String[]{"id_employee", "empl_surname", "empl_name", "empl_patronymic", "empl_role", "salary", "date_of_birth", "date_of_start", "phone_number", "city", "street", "zip_code"}, true);
    }

    public static DataPanel customerCards(boolean manager) {
        // Карти клієнтів можуть редагувати і менеджер, і касир; видалення дозволене тільки менеджеру.
        return new DataPanel("Customer_Card", "card_number", "cust_surname",
                new String[]{"card_number", "cust_surname", "cust_name", "cust_patronymic", "phone_number", "city", "street", "zip_code", "percent"}, manager);
    }

    public static DataPanel categories() {
        // Категорії використовуються для класифікації товарів.
        return new DataPanel("Category", "category_number", "category_name",
                new String[]{"category_number", "category_name"}, true);
    }

    public static DataPanel products() {
        // Product містить опис товару без ціни та залишку.
        return new DataPanel("Product", "id_product", "product_name",
                new String[]{"id_product", "category_number", "product_name", "manufacturer", "characteristics"}, true);
    }

    public static DataPanel storeProducts() {
        // Store_Product містить реальні одиниці товарів у магазині з UPC і ціною.
        return new DataPanel("Store_Product", "UPC", "products_number",
                new String[]{"UPC", "UPC_prom", "id_product", "selling_price", "products_number", "promotional_product"}, true);
    }

    private JPanel form() {
        // Права частина панелі містить поля для введення значень і кнопки CRUD.
        JPanel wrapper = new JPanel(new BorderLayout(6, 6));
        JPanel inputs = new JPanel(new GridLayout(0, 1, 4, 3));
        for (String column : columns) {
            JTextField field = new JTextField(16);
            fields.put(column, field);
            inputs.add(new JLabel(column));
            inputs.add(field);
        }
        JButton load = new JButton("Заповнити з рядка");
        JButton add = new JButton("Додати");
        JButton update = new JButton("Оновити");
        JButton delete = new JButton("Видалити");
        JButton print = new JButton("Друк таблиці");
        // Кнопки викликають відповідні SQL-операції.
        load.addActionListener(e -> loadSelected());
        add.addActionListener(e -> insertRow());
        update.addActionListener(e -> updateRow());
        delete.addActionListener(e -> deleteRow());
        delete.setEnabled(allowDelete);
        print.addActionListener(e -> printGrid());
        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        buttons.add(load);
        buttons.add(add);
        buttons.add(update);
        buttons.add(delete);
        buttons.add(print);
        wrapper.add(inputs, BorderLayout.CENTER);
        wrapper.add(buttons, BorderLayout.SOUTH);
        return wrapper;
    }

    private void refresh() {
        try {
            // Дані таблиці перечитуються з бази після кожної зміни.
            grid.setModel(Db.tableModel("SELECT * FROM \"" + table + "\" ORDER BY " + orderBy));
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private void loadSelected() {
        // Обраний рядок копіюється у поля форми для подальшого редагування.
        int row = grid.getSelectedRow();
        if (row < 0) {
            return;
        }
        for (int i = 0; i < columns.length; i++) {
            Object value = grid.getValueAt(row, i);
            fields.get(columns[i]).setText(value == null ? "" : value.toString());
        }
    }

    private void insertRow() {
        // INSERT будується за списком колонок конкретної таблиці.
        if (!validateForm()) {
            return;
        }
        StringJoiner cols = new StringJoiner(", ");
        StringJoiner qs = new StringJoiner(", ");
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            cols.add(columns[i]);
            qs.add("?");
            values[i] = value(columns[i]);
        }
        try {
            Db.execute("INSERT INTO \"" + table + "\" (" + cols + ") VALUES (" + qs + ")", values);
            refresh();
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private void updateRow() {
        // Первинний ключ не змінюється, він використовується в WHERE для пошуку запису.
        if (!validateForm()) {
            return;
        }
        StringJoiner set = new StringJoiner(", ");
        java.util.List<Object> values = new java.util.ArrayList<>();
        for (String column : columns) {
            if (!column.equals(pk)) {
                set.add(column + " = ?");
                values.add(value(column));
            }
        }
        values.add(value(pk));
        try {
            Db.execute("UPDATE \"" + table + "\" SET " + set + " WHERE " + pk + " = ?", values.toArray());
            refresh();
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private void deleteRow() {
        // Видалення обмежується роллю користувача через параметр allowDelete.
        if (!allowDelete) {
            return;
        }
        Object id = value(pk);
        if (JOptionPane.showConfirmDialog(this, "Видалити запис " + id + "?", "Підтвердження", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Db.execute("DELETE FROM \"" + table + "\" WHERE " + pk + " = ?", id);
            refresh();
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private Object value(String column) {
        // Порожній рядок перетворюється у NULL, щоб підтримувати необов'язкові поля.
        String text = fields.get(column).getText().trim();
        return text.isEmpty() ? null : text;
    }

    private boolean validateForm() {
        try {
            if ("Employee".equals(table)) {
                // Семантичне обмеження з вимог: працівнику має бути не менше 18 років.
                LocalDate birth = LocalDate.parse(fields.get("date_of_birth").getText().trim());
                if (birth.isAfter(LocalDate.now().minusYears(18))) {
                    throw new IllegalArgumentException("Працівнику має бути не менше 18 років");
                }
            }
            if ("Store_Product".equals(table)) {
                // Для акційного товару ціна повинна дорівнювати 80% ціни звичайного товару.
                boolean promotional = "1".equals(fields.get("promotional_product").getText().trim())
                        || "true".equalsIgnoreCase(fields.get("promotional_product").getText().trim());
                String upcProm = fields.get("UPC_prom").getText().trim();
                if (promotional && !upcProm.isEmpty()) {
                    Object normalPrice = Db.scalar("SELECT selling_price FROM Store_Product WHERE UPC = ?", upcProm);
                    if (normalPrice != null) {
                        java.math.BigDecimal expected = new java.math.BigDecimal(normalPrice.toString())
                                .multiply(new java.math.BigDecimal("0.8"))
                                .setScale(2, java.math.RoundingMode.HALF_UP);
                        java.math.BigDecimal actual = new java.math.BigDecimal(fields.get("selling_price").getText().trim())
                                .setScale(2, java.math.RoundingMode.HALF_UP);
                        if (actual.compareTo(expected) != 0) {
                            throw new IllegalArgumentException("Акційна ціна має бути " + expected + " (80% від звичайної)");
                        }
                    }
                }
            }
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Помилка перевірки даних", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void printGrid() {
        try {
            // JTable.print() відкриває стандартний механізм друку Java.
            grid.print();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
    }
}
