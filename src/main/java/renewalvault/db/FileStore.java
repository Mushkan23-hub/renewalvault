package renewalvault.db;

import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import renewalvault.security.CryptoUtil;

/**
 * Dependency-free persistence layer: serializes a List of objects,
 * encrypts the resulting bytes with AES-256-GCM using the vault's
 * derived key, and writes the ciphertext to a local file under
 * ./data. Reloads and transparently decrypts on startup. Keeps the
 * project buildable with nothing but javac + java (no external DB
 * driver, and no external crypto library -- javax.crypto ships with
 * every JDK) while ensuring the on-disk data is never readable
 * without the master password.
 *
 * @param <T> the Serializable type being stored
 */
public class FileStore<T extends Serializable> {

    private final File file;
    private final SecretKeySpec key;

    public FileStore(File dataDir, String fileName, SecretKeySpec key) {
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        this.file = new File(dataDir, fileName);
        this.key = key;
    }

    @SuppressWarnings("unchecked")
    public List<T> loadAll() {
        if (!file.exists() || file.length() == 0) {
            return new ArrayList<>();
        }
        try {
            byte[] encrypted = readAllBytes(file);
            byte[] plaintext = CryptoUtil.decrypt(encrypted, key);
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(plaintext))) {
                return (List<T>) ois.readObject();
            }
        } catch (GeneralSecurityException e) {
            System.err.println("Error: could not decrypt " + file.getName()
                    + ". The master password may be wrong, or the file has been tampered with.");
            return new ArrayList<>();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Warning: could not read " + file.getName() + " (" + e.getMessage() + "). Starting fresh.");
            return new ArrayList<>();
        }
    }

    public void saveAll(List<T> items) {
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(byteOut)) {
                oos.writeObject(items);
            }
            byte[] encrypted = CryptoUtil.encrypt(byteOut.toByteArray(), key);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(encrypted);
            }
        } catch (IOException | GeneralSecurityException e) {
            System.err.println("Error: failed to persist data to " + file.getName() + ": " + e.getMessage());
        }
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    /**
     * Byte-level re-encryption: decrypts a file with the old key and re-encrypts it with the new one,
     * without ever deserializing the contents into objects. Used by master-password rotation so it works
     * identically for items.dat, logs.dat, or any future encrypted file, regardless of what type it stores.
     * Silently does nothing if the file doesn't exist yet (e.g. a brand-new vault with no logs written yet).
     */
    public static void reencryptFile(File file, SecretKeySpec oldKey, SecretKeySpec newKey) throws IOException, GeneralSecurityException {
        if (!file.exists() || file.length() == 0) return;
        byte[] oldEncrypted = readAllBytes(file);
        byte[] plaintext = CryptoUtil.decrypt(oldEncrypted, oldKey);
        byte[] newEncrypted = CryptoUtil.encrypt(plaintext, newKey);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(newEncrypted);
        }
    }
}
