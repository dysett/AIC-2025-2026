package zlagoda;

public final class SmokeTest {
    private SmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        // Smoke-test перевіряє найважливіші частини без відкриття графічного інтерфейсу.
        Db.initialize();
        Session manager = AuthService.login("manager", "Manager123!".toCharArray());
        Session cashier = AuthService.login("cashier", "Cashier123!".toCharArray());
        if (manager == null || !manager.isManager()) {
            // Якщо ця перевірка падає, проблема в авторизації менеджера або ролі MANAGER.
            throw new IllegalStateException("Manager login failed");
        }
        if (cashier == null || !cashier.isCashier()) {
            // Якщо ця перевірка падає, проблема в авторизації касира або ролі CASHIER.
            throw new IllegalStateException("Cashier login failed");
        }
        System.out.println("manager=" + manager.employeeName());
        System.out.println("cashier=" + cashier.employeeName());
        System.out.println("employees=" + Db.scalar("SELECT COUNT(*) FROM Employee"));
        System.out.println("products=" + Db.scalar("SELECT COUNT(*) FROM Product"));
        System.out.println("manufacturers=" + Db.scalar("SELECT COUNT(manufacturer) FROM Product"));
        System.out.println("checks=" + Db.scalar("SELECT COUNT(*) FROM \"Check\""));
        System.out.println("salesTotal=" + Db.scalar("SELECT ROUND(SUM(sum_total), 2) FROM \"Check\""));
    }
}
