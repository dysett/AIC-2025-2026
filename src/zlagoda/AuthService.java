package zlagoda;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class AuthService {
    private AuthService() {
    }

    public static Session login(String username, char[] password) {
        // Авторизація виконується чистим SQL-запитом без ORM.
        // Пароль з бази не читається у відкритому вигляді, а перевіряється через salt і hash.
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
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Якщо користувача з таким логіном немає, вхід заборонено.
                    return null;
                }
                boolean ok = PasswordUtil.verify(password, rs.getString("password_salt"), rs.getString("password_hash"));
                if (!ok) {
                    // Якщо хеш введеного пароля не збігся з хешем у базі, вхід теж заборонено.
                    return null;
                }
                // Якщо пароль правильний, створюється об'єкт поточної сесії.
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
