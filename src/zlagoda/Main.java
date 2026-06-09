package zlagoda;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import zlagoda.ui.LoginDialog;
import zlagoda.ui.MainFrame;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                // При першому запуску створюється база даних і додаються тестові записи.
                Db.initialize();
                LoginDialog dialog = new LoginDialog(null);
                dialog.setVisible(true);
                Session session = dialog.getSession();
                if (session == null) {
                    System.exit(0);
                }
                // Після успішного входу відкривається головне вікно з урахуванням ролі.
                new MainFrame(session).setVisible(true);
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, ex.getMessage(), "ZLAGODA startup error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
