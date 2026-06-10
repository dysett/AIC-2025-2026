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
        // Усі Swing-вікна створюються в Event Dispatch Thread.
        // Це стандартне правило Swing: графічні компоненти мають оновлюватися в одному UI-потоці.
        SwingUtilities.invokeLater(() -> {
            try {
                // Системний LookAndFeel робить кнопки, таблиці й поля схожими на звичайні елементи Windows.
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

                // Перед показом форми входу ініціалізується база даних.
                // Якщо файлу data/zlagoda.db ще немає, Db.initialize() створює таблиці й тестові записи.
                // Якщо база вже існує, повторні тестові записи не додаються.
                Db.initialize();

                // Діалог входу модальний: виконання зупиняється на setVisible(true),
                // доки користувач не введе логін/пароль або не закриє форму.
                LoginDialog dialog = new LoginDialog(null);
                dialog.setVisible(true);

                // Після закриття діалогу читається Session.
                // Якщо сесія null, користувач натиснув "Вийти" або закрив форму входу.
                Session session = dialog.getSession();
                if (session == null) {
                    System.exit(0);
                }

                // MainFrame будує вкладки відповідно до ролі:
                // менеджер бачить довідники й адміністративні функції, касир - продажі та власні звіти.
                new MainFrame(session).setVisible(true);
            } catch (Exception ex) {
                // Будь-яка критична помилка запуску показується користувачу,
                // щоб програма не завершувалась без пояснення.
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null, ex.getMessage(), "ZLAGODA startup error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
