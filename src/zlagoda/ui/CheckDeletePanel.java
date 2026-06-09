package zlagoda.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import zlagoda.Db;

public class CheckDeletePanel extends JPanel {
    private final JTable table = new JTable();

    public CheckDeletePanel() {
        super(new BorderLayout(8, 8));
        add(toolbar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        refresh();
    }

    private JPanel toolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refresh = new JButton("Оновити");
        JButton delete = new JButton("Видалити обраний чек");
        JButton print = new JButton("Друк списку чеків");
        refresh.addActionListener(e -> refresh());
        delete.addActionListener(e -> deleteSelectedCheck());
        print.addActionListener(e -> printChecks());
        panel.add(refresh);
        panel.add(delete);
        panel.add(print);
        return panel;
    }

    private void refresh() {
        try {
            // Менеджеру показуються всі чеки разом із касиром і картою клієнта.
            table.setModel(Db.tableModel("""
                    SELECT CH.check_number, CH.print_date,
                           E.empl_surname || ' ' || E.empl_name AS cashier,
                           CH.card_number, CH.sum_total, CH.vat
                    FROM "Check" CH
                    JOIN Employee E ON E.id_employee = CH.id_employee
                    ORDER BY CH.print_date DESC
                    """));
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private void deleteSelectedCheck() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Оберіть чек для видалення");
            return;
        }
        String checkNumber = String.valueOf(table.getValueAt(row, 0));
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Видалити чек " + checkNumber + "? Кількість товарів буде повернена на склад.",
                "Підтвердження",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try (Connection con = Db.connect()) {
            con.setAutoCommit(false);
            try {
                // Перед видаленням повертаємо продану кількість у Store_Product.
                Db.execute(con, """
                        UPDATE Store_Product
                        SET products_number = products_number + (
                            SELECT COALESCE(SUM(S.product_number), 0)
                            FROM Sale S
                            WHERE S.check_number = ? AND S.UPC = Store_Product.UPC
                        )
                        WHERE UPC IN (SELECT UPC FROM Sale WHERE check_number = ?)
                        """, checkNumber, checkNumber);
                // Після видалення Check пов'язані рядки Sale видаляються через ON DELETE CASCADE.
                Db.execute(con, "DELETE FROM \"Check\" WHERE check_number = ?", checkNumber);
                con.commit();
                refresh();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            showError(ex);
        }
    }

    private void printChecks() {
        try {
            // Друк списку чеків реалізований через стандартний JTable.print().
            table.print();
        } catch (Exception ex) {
            showError(ex);
        }
    }

    private void showError(Exception ex) {
        JOptionPane.showMessageDialog(this, ex.getMessage(), "SQL error", JOptionPane.ERROR_MESSAGE);
    }
}
