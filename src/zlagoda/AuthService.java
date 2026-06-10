package zlagoda;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class AuthService {
    private AuthService() {
    }

    public static Session login(String username, char[] password) {
        // Метод виконує повний цикл аутентифікації:
        // 1) знаходить користувача за логіном;
        // 2) читає salt і password_hash;
        // 3) рахує хеш для введеного пароля;
        // 4) створює Session з роллю та прив'язкою до працівника.
        // Сам пароль у відкритому вигляді в базі не зберігається і з бази не читається.
        String sql = """
                SELECT u.user_id, u.username, u.password_hash, u.password_salt, u.role,
                       u.id_employee,
                       COALESCE(e.empl_surname || ' ' || e.empl_name, u.username) AS employee_name
                FROM App_User u
                LEFT JOIN Employee e ON e.id_employee = u.id_employee
                WHERE u.username = ?
                """;
        try (Connection con = Db.connect();
             PreparedStatement ps = con.prepareStatement(sql)) {
            // Логін передається як параметр PreparedStatement.
            // Це важливо, бо введений текст не вставляється напряму в SQL-рядок.
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Якщо користувача з таким логіном немає, вхід заборонено.
                    return null;
                }
                // PasswordUtil.verify не розшифровує пароль, а повторно обчислює PBKDF2-хеш
                // з тим самим salt і порівнює його з password_hash із таблиці App_User.
                boolean ok = PasswordUtil.verify(password, rs.getString("password_salt"), rs.getString("password_hash"));
                if (!ok) {
                    // Якщо хеш введеного пароля не збігся з хешем у базі, вхід теж заборонено.
                    return null;
                }
                // Session не зберігає пароль. У ній є тільки user_id, username, role,
                // id_employee і відображуване ім'я працівника для інтерфейсу.
                return new Session(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        rs.getString("id_employee"),
                        rs.getString("employee_name")
                );
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Login failed", ex);
        }
    }
}
