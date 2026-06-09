package zlagoda.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import zlagoda.Db;
import zlagoda.Session;

public class SalePanel extends JPanel {
    // Панель продажу використовується касиром для створення нового чека.
    private final Session session;
    private final JTextField cardNumber = new JTextField(13);
    private final JTextField upc = new JTextField(12);
    private final JTextField quantity = new JTextField("1", 5);
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"UPC", "Назва", "К-сть", "Ціна", "Сума"}, 0);
    private final JTable lines = new JTable(model);
    private final JTextArea receiptPreview = new JTextArea(8, 50);
    private final List<Line> saleLines = new ArrayList<>();

    public SalePanel(Session session) {
        super(new BorderLayout(8, 8));
        this.session = session;
        // Інтерфейс складається з форми додавання позиції, таблиці позицій і попереднього перегляду чека.
        add(top(), BorderLayout.NORTH);
        add(new JScrollPane(lines), BorderLayout.CENTER);
        add(bottom(), BorderLayout.SOUTH);
    }

    private JPanel top() {
        // Тут касир вводить карту клієнта, UPC товару та кількість.
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Додати позицію");
        JButton clear = new JButton("Очистити");
        add.addActionListener(e -> addLine());
        clear.addActionListener(e -> clear());
        p.add(new JLabel("Карта"));
        p.add(cardNumber);
        p.add(new JLabel("UPC"));
        p.add(upc);
        p.add(new JLabel("К-сть"));
        p.add(quantity);
        p.add(add);
        p.add(clear);
        return p;
    }

    private JPanel bottom() {
        // Нижня частина показує текст чека та містить кнопки створення/друку.
        JPanel p = new JPanel(new BorderLayout(6, 6));
        receiptPreview.setEditable(false);
        JButton create = new JButton("Створити чек");
        JButton print = new JButton("Друк чека");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(create);
        buttons.add(print);
        create.addActionListener(e -> createCheck());
        print.addActionListener(e -> printReceipt());
        p.add(new JScrollPane(receiptPreview), BorderLayout.CENTER);
        p.add(buttons, BorderLayout.SOUTH);
        return p;
    }

    private void addLine() {
        try {
            // Перед додаванням позиції перевіряємо кількість і наявність товару на складі.
            int qty = Integer.parseInt(quantity.getText().trim());
            if (qty <= 0) {
                throw new NumberFormatException("Quantity must be positive");
            }
            try (Connection con = Db.connect();
                 PreparedStatement ps = con.prepareStatement("""
                         -- Беремо назву, ціну і залишок товару за UPC.
                         SELECT SP.UPC, P.product_name, SP.selling_price, SP.products_number
                         FROM Store_Product SP
                         JOIN Product P ON P.id_product = SP.id_product
                         WHERE SP.UPC = ?
                         """)) {
                ps.setString(1, upc.getText().trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(this, "UPC не знайдено");
                        return;
                    }
                    int stock = rs.getInt("products_number");
                    if (qty > stock) {
                        JOptionPane.showMessageDialog(this, "Недостатньо товару на складі: " + stock);
                        return;
                    }
                    BigDecimal price = rs.getBigDecimal("selling_price");
                    // Ціна продажу фіксується в рядку чека, щоб історія не змінювалась після переоцінки.
                    Line line = new Line(rs.getString("UPC"), rs.getString("product_name"), qty, price);
                    saleLines.add(line);
                    model.addRow(new Object[]{line.upc, line.name, line.quantity, line.price, line.total()});
                    updatePreview(null);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Sale error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void createCheck() {
        if (saleLines.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Додайте хоча б одну позицію");
            return;
        }
        try (Connection con = Db.connect()) {
            // AutoCommit вимикається, бо створення чека складається з кількох SQL-операцій.
            con.setAutoCommit(false);
            try {
                // Чек і всі його товари записуються в одній транзакції.
                String checkNumber = nextCheckNumber(con);
                BigDecimal discount = customerDiscount(con);
                // Спочатку рахуємо суму всіх рядків без знижки.
                BigDecimal total = saleLines.stream()
                        .map(Line::total)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (discount.compareTo(BigDecimal.ZERO) > 0) {
                    // Якщо карта клієнта існує, загальна сума зменшується на її відсоток.
                    total = total.multiply(BigDecimal.ONE.subtract(discount.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
                }
                total = total.setScale(2, RoundingMode.HALF_UP);
                // За вимогами ПДВ становить 20% від загальної суми.
                BigDecimal vat = total.multiply(BigDecimal.valueOf(0.2)).setScale(2, RoundingMode.HALF_UP);
                String card = cardNumber.getText().trim().isEmpty() ? null : cardNumber.getText().trim();
                String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                // Таблиця Check зберігає загальну інформацію про чек.
                Db.execute(con, "INSERT INTO \"Check\" VALUES (?,?,?,?,?,?)",
                        checkNumber, session.employeeId(), card, date, total, vat);
                for (Line line : saleLines) {
                    // Таблиця Sale зберігає конкретні товари, кількість і ціну в цьому чеку.
                    Db.execute(con, "INSERT INTO Sale VALUES (?,?,?,?)",
                            line.upc, checkNumber, line.quantity, line.price);
                    // Після продажу залишок товару у магазині зменшується.
                    Db.execute(con, "UPDATE Store_Product SET products_number = products_number - ? WHERE UPC = ?",
                            line.quantity, line.upc);
                }
                // Якщо всі операції успішні, зміни остаточно записуються у базу.
                con.commit();
                updatePreview(checkNumber);
                JOptionPane.showMessageDialog(this, "Чек створено: " + checkNumber);
                clearLinesOnly();
            } catch (Exception ex) {
                // Якщо сталася помилка хоча б в одній операції, чек не створюється частково.
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BigDecimal customerDiscount(Connection con) throws SQLException {
        // Якщо покупець має карту клієнта, з неї береться відсоток знижки.
        String card = cardNumber.getText().trim();
        if (card.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT percent FROM Customer_Card WHERE card_number = ?")) {
            ps.setString(1, card);
            try (ResultSet rs = ps.executeQuery()) {
                // Якщо карта не знайдена, продаж все одно можливий, але без знижки.
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    private String nextCheckNumber(Connection con) throws SQLException {
        // Номер чека формується автоматично за кількістю вже створених чеків.
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) + 1 FROM \"Check\"");
             ResultSet rs = ps.executeQuery()) {
            int n = rs.next() ? rs.getInt(1) : 1;
            return String.format("CHK%07d", n);
        }
    }

    private void updatePreview(String checkNumber) {
        // Попередній перегляд використовується і для контролю касира, і для друку чека.
        StringBuilder sb = new StringBuilder();
        sb.append("ZLAGODA supermarket\n");
        if (checkNumber != null) {
            sb.append("Check: ").append(checkNumber).append('\n');
        }
        sb.append("Cashier: ").append(session.employeeName()).append('\n');
        sb.append("------------------------------\n");
        for (Line line : saleLines) {
            sb.append(line.name).append(" x").append(line.quantity)
                    .append(" = ").append(line.total()).append('\n');
        }
        receiptPreview.setText(sb.toString());
    }

    private void clear() {
        // Очищення форми використовується після продажу або при скасуванні введення.
        cardNumber.setText("");
        upc.setText("");
        quantity.setText("1");
        clearLinesOnly();
        receiptPreview.setText("");
    }

    private void clearLinesOnly() {
        saleLines.clear();
        model.setRowCount(0);
    }

    private void printReceipt() {
        try {
            // Друк чека реалізовано через стандартний друк текстового компонента Swing.
            receiptPreview.print();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Print error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private record Line(String upc, String name, int quantity, BigDecimal price) {
        BigDecimal total() {
            // Сума рядка = ціна товару у момент продажу * кількість.
            return price.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
