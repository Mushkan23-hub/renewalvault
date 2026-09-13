package renewalvault.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Dependency-free AES-256-GCM encryption backed by PBKDF2 key
 * derivation, using only classes shipped in the JDK (javax.crypto) --
 * no external crypto library required, keeping the project's
 * "javac + java, nothing else" build promise intact.
 *
 * <p>This is what turns RenewalVault's local .dat files from plain
 * serialized objects into an actual encrypted vault: every save is
 * encrypted with a key derived from the user's master password, and
 * every load has to prove it holds the right password before it can
 * decrypt anything. A stolen data/ folder is useless without the
 * master password.
 */
public final class CryptoUtil {

    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BITS = 256;
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int SALT_LENGTH_BYTES = 16;

    private CryptoUtil() { }

    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /** Derives a 256-bit AES key from a password + salt using PBKDF2-HMAC-SHA256. */
    public static SecretKeySpec deriveKey(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** Encrypts plaintext, prefixing the output with a fresh random IV so it can be recovered at decrypt time. */
    public static byte[] encrypt(byte[] plaintext, SecretKeySpec key) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
    }

    /** Reverses {@link #encrypt}: strips the leading IV, then decrypts + authenticates the remainder. */
    public static byte[] decrypt(byte[] ivAndCiphertext, SecretKeySpec key) throws GeneralSecurityException {
        ByteBuffer buffer = ByteBuffer.wrap(ivAndCiphertext);
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    /** Constant-time comparison to avoid leaking password-check timing information. */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return java.security.MessageDigest.isEqual(a, b);
    }
}
