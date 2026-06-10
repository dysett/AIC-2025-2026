package zlagoda;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordUtil {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String newSalt() {
        // Salt - це випадкові байти, які додаються до пароля перед хешуванням.
        // Завдяки salt однаковий пароль у двох користувачів матиме різні password_hash.
        // У базі salt можна зберігати відкрито, бо він не є паролем.
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(char[] password, String saltBase64) {
        try {
            // Base64 використовується тільки як зручний текстовий формат для зберігання байтів у БД.
            // Перед обчисленням хешу salt повертається з Base64 назад у масив байтів.
            byte[] salt = Base64.getDecoder().decode(saltBase64);

            // PBKDF2WithHmacSHA256 навмисно виконує багато ітерацій.
            // Це робить перевірку одного пароля трохи дорожчою, але значно ускладнює масовий перебір.
            KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot hash password", ex);
        }
    }

    public static boolean verify(char[] password, String saltBase64, String expectedHash) {
        // Для перевірки пароль не розшифровується: розшифрувати хеш неможливо.
        // Метод повторно обчислює хеш для введеного пароля з тим самим salt.
        String actualHash = hash(password, saltBase64);
        byte[] actual = Base64.getDecoder().decode(actualHash);
        byte[] expected = Base64.getDecoder().decode(expectedHash);

        // MessageDigest.isEqual порівнює масиви байтів без раннього виходу при першій відмінності.
        // Це зменшує витік інформації через час виконання порівняння.
        return MessageDigest.isEqual(actual, expected);
    }
}
