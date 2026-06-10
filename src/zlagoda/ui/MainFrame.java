package zlagoda.ui;

import java.awt.BorderLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import zlagoda.Session;

public class MainFrame extends JFrame {
    public MainFrame(Session session) {
        super("ZLAGODA AIS - " + session.employeeName() + " (" + session.role() + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JTabbedPane tabs = new JTabbedPane();
        // Базові вкладки доступні обом ролям.
        tabs.addTab("Пошук", new SearchPanel());
        tabs.addTab("Карти клієнтів", DataPanel.customerCards(session.isManager()));
        if (session.isCashier()) {
            // Створення чеків доступне тільки касиру.
            tabs.addTab("Продаж", new SalePanel(session));
        }
        if (session.isManager()) {
            // Менеджер має доступ до довідників і повного редагування даних.
            tabs.addTab("Працівники", DataPanel.employees());
            tabs.addTab("Категорії", DataPanel.categories());
            tabs.addTab("Товари", DataPanel.products());
            tabs.addTab("Товари у магазині", DataPanel.storeProducts());
            // Окрема вкладка для менеджерського видалення чеків.
            tabs.addTab("Чеки", new CheckDeletePanel());
        }
        tabs.addTab("Звіти", new ReportPanel(session));
        add(tabs, BorderLayout.CENTER);

        // Нижній рядок показує тільки поточного користувача і його роль.
        JPanel status = new JPanel(new BorderLayout());
        status.add(new JLabel("  Користувач: " + session.username()
                + " | Роль: " + (session.isManager() ? "Менеджер" : "Касир")), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }
}
