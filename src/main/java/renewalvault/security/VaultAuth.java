package renewalvault.security;

import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Handles the master-password lifecycle for the encrypted vault:
 * first-run setup, unlock verification, and derivation of the AES
 * key used everywhere else in the app. Nothing about the password
 * itself is ever stored -- only a random salt and a verification
 * hash derived from it, exactly like a real authentication system
 * should be built (never store the secret itself).
 *
 * <p>Metadata lives in {@code data/vault.meta} as plain hex text
 * (salt, then a verifier derived from the key). This file contains
 * no usable secret on its own: without the correct master password,
 * the salt is useless for decrypting anything.
 */
public class VaultAuth {

    private final File metaFile;

    public VaultAuth(File dataDir) {
        this.metaFile = new File(dataDir, "vault.meta");
    }

    public boolean isFirstRun() {
        return !metaFile.exists();
    }

    /** First-run setup: derives a key from the chosen password, records a salt + verifier, returns the key. */
    public SecretKeySpec setUpNewVault(char[] password) throws GeneralSecurityException, IOException {
        byte[] salt = CryptoUtil.generateSalt();
        SecretKeySpec key = CryptoUtil.deriveKey(password, salt);
        byte[] verifier = buildVerifier(key, salt);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(metaFile))) {
            writer.write(HexFormat.of().formatHex(salt));
            writer.newLine();
            writer.write(HexFormat.of().formatHex(verifier));
        }
        return key;
    }

    /** Unlock flow: re-derives the key from the entered password and checks it against the stored verifier. */
    public SecretKeySpec unlock(char[] password) throws GeneralSecurityException, IOException {
        String[] lines = Files.readString(metaFile.toPath()).split("\\R");
        byte[] salt = HexFormat.of().parseHex(lines[0].trim());
        byte[] expectedVerifier = HexFormat.of().parseHex(lines[1].trim());

        SecretKeySpec key = CryptoUtil.deriveKey(password, salt);
        byte[] actualVerifier = buildVerifier(key, salt);

        if (!CryptoUtil.constantTimeEquals(expectedVerifier, actualVerifier)) {
            return null; // wrong password
        }
        return key;
    }

    /**
     * A value derivable only from the correct key, safe to persist for later comparison. Deliberately uses
     * HMAC-SHA256 (deterministic) rather than AES-GCM (which is randomized per call via a fresh IV) --
     * the verifier must produce the exact same bytes every time it's computed from the same key, or
     * unlocking with the right password would fail unpredictably.
     */
    private byte[] buildVerifier(SecretKeySpec key, byte[] salt) throws GeneralSecurityException {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
        mac.update(salt);
        return mac.doFinal("RENEWALVAULT-VERIFIER".getBytes());
    }

    /**
     * Utility for reading a password from the console without echoing it, falling back gracefully if
     * unavailable. The caller's Scanner is reused for the fallback path (rather than creating a new one)
     * because multiple Scanner instances wrapping the same System.in independently buffer input and can
     * silently drop lines when input is piped/redirected instead of typed live at a terminal.
     */
    public static char[] readPassword(String prompt, java.util.Scanner fallbackScanner) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword(prompt);
        }
        // Fallback for IDEs / redirected input where System.console() is null: echoes the input.
        System.out.print(prompt + " (input will be visible - no console detected) ");
        return fallbackScanner.nextLine().toCharArray();
    }

    /** Generates a short random recovery hint id (not a password reset -- there is no backdoor -- just a label). */
    public static String newSessionNonce() {
        byte[] b = new byte[4];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
