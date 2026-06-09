package zlagoda.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.SQLException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import zlagoda.Db;

public class SearchPanel extends JPanel {
    // Панель містить типові запити для менеджера і касира відповідно до вимог.
    private final JComboBox<String> mode = new JComboBox<>(new String[]{
        "Усі товари за назвою",
        "Товари у магазині за назвою",
        "Пошук товару за назвою",
        "Пошук за категорією",
        "Пошук клієнта за прізвищем",
        "Акційні товари",
        "Не акційні товари",
        "Пошук за UPC"
    });
    private final JTextField filter = new JTextField(24);
    private final JTable table = new JTable();

    public SearchPanel() {
        super(new BorderLayout(8, 8));
        // Верхня панель містить тип запиту, фільтр і кнопки виконання/друку.
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton run = new JButton("Показати");
        JButton print = new JButton("Друк");
        run.addActionListener(e -> runSearch());
        print.addActionListener(e -> printTable());
        top.add(new JLabel("Запит"));
        top.add(mode);
        top.add(new JLabel("Фільтр"));
        top.add(filter);
        top.add(run);
        top.add(print);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        runSearch();
    }

    private void runSearch() {
        try {
            String selected = (String) mode.getSelectedItem();
            String value = filter.getText().trim();
            // Кожен режим виконує окремий чистий SQL-запит.
            switch (selected) {
                case "Усі товари за назвою" -> table.setModel(Db.tableModel("""
                        -- Список усіх товарів разом з назвою категорії.
                        SELECT P.id_product, P.product_name, P.manufacturer, C.category_name, P.characteristics
                        FROM Product P
                        JOIN Category C ON C.category_number = P.category_number
                        ORDER BY P.product_name
                        """));
                case "Товари у магазині за назвою" -> table.setModel(Db.tableModel("""
                        -- Показує UPC, ціну та залишок кожного товару у магазині.
                        SELECT SP.UPC, P.product_name, C.category_name, SP.selling_price,
                               SP.products_number, SP.promotional_product
                        FROM Store_Product SP
                        JOIN Product P ON P.id_product = SP.id_product
                        JOIN Category C ON C.category_number = P.category_number
                        ORDER BY P.product_name
                        """));
                case "Пошук товару за назвою" -> table.setModel(Db.tableModel("""
                        -- LIKE використовується для часткового пошуку за назвою.
                        SELECT P.id_product, P.product_name, P.manufacturer, C.category_name, P.characteristics
                        FROM Product P
                        JOIN Category C ON C.category_number = P.category_number
                        WHERE lower(P.product_name) LIKE lower(?)
                        ORDER BY P.product_name
                        """, "%" + value + "%"));
                case "Пошук за категорією" -> table.setModel(Db.tableModel("""
                        -- Запит знаходить товари, що належать категорії з введеною назвою.
                        SELECT P.id_product, P.product_name, P.manufacturer, C.category_name, P.characteristics
                        FROM Product P
                        JOIN Category C ON C.category_number = P.category_number
                        WHERE lower(C.category_name) LIKE lower(?)
                        ORDER BY P.product_name
                        """, "%" + value + "%"));
                case "Пошук клієнта за прізвищем" -> table.setModel(Db.tableModel("""
                        -- Касир може знайти постійного клієнта за прізвищем або його частиною.
                        SELECT card_number, cust_surname, cust_name, cust_patronymic,
                               phone_number, city, street, zip_code, percent
                        FROM Customer_Card
                        WHERE lower(cust_surname) LIKE lower(?)
                        ORDER BY cust_surname, cust_name
                        """, "%" + value + "%"));
                case "Акційні товари" -> table.setModel(Db.tableModel("""
                        -- Акційні товари мають promotional_product = 1.
                        SELECT SP.UPC, P.product_name, SP.selling_price, SP.products_number
                        FROM Store_Product SP
                        JOIN Product P ON P.id_product = SP.id_product
                        WHERE SP.promotional_product = 1
                        ORDER BY SP.products_number, P.product_name
                        """));
                case "Не акційні товари" -> table.setModel(Db.tableModel("""
                        -- Звичайні товари мають promotional_product = 0.
                        SELECT SP.UPC, P.product_name, SP.selling_price, SP.products_number
                        FROM Store_Product SP
                        JOIN Product P ON P.id_product = SP.id_product
                        WHERE SP.promotional_product = 0
                        ORDER BY SP.products_number, P.product_name
                        """));
                case "Пошук за UPC" -> table.setModel(Db.tableModel("""
                        -- UPC однозначно ідентифікує товар у магазині.
                        SELECT SP.UPC, SP.selling_price, SP.products_number,
                               P.product_name, P.manufacturer, P.characteristics
                        FROM Store_Product SP
                        JOIN Product P ON P.id_product = SP.id_product
                        WHERE SP.UPC = ?
                        """, value));
                default -> throw new IllegalStateException("Unknown search mode");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void printTable() {
        try {
            table.print();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Print error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
