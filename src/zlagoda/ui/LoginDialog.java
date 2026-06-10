package zlagoda.ui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.JTextField;
import zlagoda.AuthService;
import zlagoda.Session;

public class LoginDialog extends JDialog {
    // Для зручної демонстрації поля вже заповнені обліковим записом менеджера.
    private final JTextField username = new JTextField("manager", 18);
    private final JPasswordField password = new JPasswordField("Manager123!", 18);
    private Session session;

    public LoginDialog(Frame owner) {
        super(owner, "ZLAGODA - вхід", true);
        // Діалог модальний: поки користувач не увійде, головне вікно не відкривається.
        setLayout(new BorderLayout(12, 12));
        add(form(), BorderLayout.CENTER);
        add(buttons(), BorderLayout.SOUTH);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
        // Під час запуску з PowerShell діалог входу іноді відкривається позаду інших вікон.
        // Примусово піднімаємо його поверх екрана, щоб користувач одразу бачив форму входу.
        setAlwaysOnTop(true);
        SwingUtilities.invokeLater(() -> {
            toFront();
            requestFocus();
            username.requestFocusInWindow();
        });
    }

    public Session getSession() {
        return session;
    }

    private JPanel form() {
        // GridBagLayout використовується для компактної форми з підписами та полями.
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 18, 8, 18));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Логін"), c);
        c.gridx = 1;
        panel.add(username, c);
        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Пароль"), c);
        c.gridx = 1;
        panel.add(password, c);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        panel.add(new JLabel("<html><small>manager / Manager123! або cashier / Cashier123!</small></html>"), c);
        return panel;
    }

    private JPanel buttons() {
        JPanel panel = new JPanel();
        JButton login = new JButton("Увійти");
        JButton cancel = new JButton("Вийти");
        // Кнопка "Увійти" запускає перевірку логіна і пароля.
        login.addActionListener(e -> doLogin());
        cancel.addActionListener(e -> dispose());
        // Enter у вікні входу натискає кнопку входу.
        getRootPane().setDefaultButton(login);
        panel.add(login);
        panel.add(cancel);
        return panel;
    }

    private void doLogin() {
        // AuthService перевіряє логін, пароль і повертає сесію з роллю користувача.
        session = AuthService.login(username.getText().trim(), password.getPassword());
        if (session == null) {
            JOptionPane.showMessageDialog(this, "Невірний логін або пароль", "Помилка входу", JOptionPane.ERROR_MESSAGE);
            return;
        }
        dispose();
    }
}
