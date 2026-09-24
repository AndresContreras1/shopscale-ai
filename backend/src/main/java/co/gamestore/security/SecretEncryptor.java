package co.gamestore.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * Encrypts the values that are credentials in themselves, such as a TOTP shared secret.
 *
 * <p>A password can be hashed because it only ever needs to be compared. A shared secret has to be
 * read back to compute the next code, so hashing is not an option and encryption is. AES with a
 * random initialisation vector per value, from Spring Security's standard encryptor.
 *
 * <p>The key comes from the environment. Losing it means staff have to enrol their authenticator
 * again; leaking it means the second factor is worth nothing, so it belongs with the other secrets.
 */
@Component
public class SecretEncryptor {

    private final TextEncryptor encryptor;

    public SecretEncryptor(@Value("${app.security.secret-key:dev-only-key-change-me}") String key,
                           @Value("${app.security.secret-salt:5c0744940b5c369b}") String salt) {
        // delux: AES-256 in GCM with a random initialisation vector per value.
        this.encryptor = Encryptors.delux(key, salt);
    }

    public String encrypt(String plain) {
        return plain == null ? null : encryptor.encrypt(plain);
    }

    public String decrypt(String encrypted) {
        return encrypted == null ? null : encryptor.decrypt(encrypted);
    }
}
