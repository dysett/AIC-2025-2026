package zlagoda;

public record Session(
        int userId,
        String username,
        String role,
        String employeeId,
        String employeeName
) {
    // Сесія зберігає дані користувача після входу і використовується для перевірки прав.
    public boolean isManager() {
        return "MANAGER".equalsIgnoreCase(role);
    }

    public boolean isCashier() {
        return "CASHIER".equalsIgnoreCase(role);
    }
}
