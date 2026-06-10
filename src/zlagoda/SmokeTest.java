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
        System.out.println("courseworkGroupedRows=" + Db.scalar("""
                SELECT COUNT(*)
                FROM (
                    SELECT C.category_number
                    FROM "Check" CH
                    JOIN Sale S ON S.check_number = CH.check_number
                    JOIN Store_Product SP ON SP.UPC = S.UPC
                    JOIN Product P ON P.id_product = SP.id_product
                    JOIN Category C ON C.category_number = P.category_number
                    WHERE date(CH.print_date) BETWEEN date(?) AND date(?)
                    GROUP BY C.category_number, C.category_name
                )
                """, "2026-06-01", "2026-06-30"));
        System.out.println("courseworkAllCategoryCustomers=" + Db.scalar("""
                SELECT COUNT(*)
                FROM Customer_Card CC
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM Category C
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM "Check" CH
                        JOIN Sale S ON S.check_number = CH.check_number
                        JOIN Store_Product SP ON SP.UPC = S.UPC
                        JOIN Product P ON P.id_product = SP.id_product
                        WHERE CH.card_number = CC.card_number
                          AND P.category_number = C.category_number
                    )
                )
                """));
    }
}
