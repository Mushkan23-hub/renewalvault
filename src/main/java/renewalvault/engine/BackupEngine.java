package renewalvault.engine;

import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Module 7: Backup / Restore.
 *
 * Bundles the entire {@code data/} directory (items.dat, logs.dat,
 * vault.meta -- still AES-256 encrypted, exactly as they sit on disk)
 * into a single portable {@code .rvbackup} file, and can restore that
 * bundle back into a fresh {@code data/} directory on any machine.
 *
 * <p>This is different from the CSV export: CSV is a deliberately
 * plaintext, human-editable snapshot of your items for spreadsheets;
 * a .rvbackup file is the real encrypted vault itself, byte-for-byte,
 * still requiring the original master password to unlock after
 * restoring. Uses only {@code java.util.zip}, which ships with every
 * JDK, so no new external dependency is introduced.
 */
public class BackupEngine {

    private static final String[] VAULT_FILES = { "items.dat", "logs.dat", "vault.meta" };

    /** Zips every present vault file (still encrypted) into a single portable backup file. */
    public void exportBackup(File dataDir, File outFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outFile))) {
            for (String name : VAULT_FILES) {
                File f = new File(dataDir, name);
                if (!f.exists()) continue;
                zos.putNextEntry(new ZipEntry(name));
                zos.write(Files.readAllBytes(f.toPath()));
                zos.closeEntry();
            }
        }
    }

    /**
     * Extracts a .rvbackup file into the given data directory. Intended for restoring onto a fresh
     * machine (no existing vault.meta) -- the caller is responsible for confirming with the user before
     * calling this if a vault already exists there, since it overwrites items.dat/logs.dat/vault.meta.
     */
    public void restoreBackup(File backupFile, File dataDir) throws IOException {
        if (!dataDir.exists()) dataDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(backupFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File out = new File(dataDir, new File(entry.getName()).getName()); // strip any path, flatten into dataDir
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    zis.transferTo(fos);
                }
                zis.closeEntry();
            }
        }
    }
}
