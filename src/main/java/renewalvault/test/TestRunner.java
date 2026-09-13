package renewalvault.test;

import renewalvault.db.FileStore;
import renewalvault.engine.*;
import renewalvault.model.ReminderLog;
import renewalvault.model.TrackedItem;
import renewalvault.security.CryptoUtil;
import renewalvault.security.VaultAuth;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-rolled, zero-dependency regression suite -- no JUnit, no
 * Maven/Gradle, nothing outside the JDK, in keeping with the rest of
 * the project. Every test runs against a throwaway temp directory
 * (never the user's real ./data), so it's always safe to run.
 *
 * Run with:
 *   java -cp out renewalvault.test.TestRunner
 *
 * Exits with code 0 if every test passes, 1 if any fail (so it can be
 * wired into a CI step later if desired).
 */
public class TestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("Running RenewalVault self-tests...\n");

        run("AES-GCM encrypt/decrypt round trip", TestRunner::testCryptoRoundTrip);
        run("VaultAuth: correct password unlocks, wrong password rejected", TestRunner::testVaultAuthUnlock);
        run("Data at rest contains no plaintext item names", TestRunner::testEncryptionAtRest);
        run("Risk score ranks overdue + high-stakes items above low-stakes ones", TestRunner::testRiskScoreOrdering);
        run("Recurrence roll-forward (weekly/monthly/yearly/custom/one-time)", TestRunner::testRecurrenceRollForward);
        run("Hash chain: valid chain passes, broken link is detected", TestRunner::testHashChainIntegrity);
        run("CSV export/import round trip preserves item data", TestRunner::testCsvRoundTrip);
        run("Undo reverses add and delete", TestRunner::testUndo);
        run("Full encrypted backup/restore preserves the vault", TestRunner::testBackupRestore);
        run("Duplicate item name is detected on add", TestRunner::testDuplicateDetection);
        run("On-time renewal streak increments, late renewal resets it", TestRunner::testRenewalStreak);
        run("Master password rotation re-encrypts existing data correctly", TestRunner::testPasswordRotation);

        System.out.println("\n" + passed + " passed, " + failed + " failed.");
        if (failed > 0) System.exit(1);
    }

    // ---------------------------------------------------------------
    // Test harness
    // ---------------------------------------------------------------

    private interface TestCase { void run() throws Exception; }

    private static void run(String name, TestCase test) {
        try {
            test.run();
            System.out.println("  [PASS] " + name);
            passed++;
        } catch (AssertionError e) {
            System.out.println("  [FAIL] " + name + " -- " + e.getMessage());
            failed++;
        } catch (Exception e) {
            System.out.println("  [ERROR] " + name + " -- " + e);
            failed++;
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static File newTempDir() throws Exception {
        File dir = Files.createTempDirectory("renewalvault-test-").toFile();
        dir.deleteOnExit();
        return dir;
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    private static void testCryptoRoundTrip() throws Exception {
        byte[] salt = CryptoUtil.generateSalt();
        SecretKeySpec key = CryptoUtil.deriveKey("hunter2".toCharArray(), salt);
        byte[] plaintext = "The quick brown fox".getBytes();
        byte[] encrypted = CryptoUtil.encrypt(plaintext, key);
        assertTrue(!new String(encrypted).contains("quick brown fox"), "ciphertext should not contain the plaintext");
        byte[] decrypted = CryptoUtil.decrypt(encrypted, key);
        assertEquals(new String(plaintext), new String(decrypted), "decrypted text should match original");
    }

    private static void testVaultAuthUnlock() throws Exception {
        File dir = newTempDir();
        VaultAuth auth = new VaultAuth(dir);
        assertTrue(auth.isFirstRun(), "fresh temp dir should report first run");
        auth.setUpNewVault("correct-horse-battery".toCharArray());
        assertTrue(!auth.isFirstRun(), "vault.meta should now exist");

        SecretKeySpec correctKey = auth.unlock("correct-horse-battery".toCharArray());
        assertTrue(correctKey != null, "correct password should unlock");

        SecretKeySpec wrongKey = auth.unlock("wrong-password".toCharArray());
        assertTrue(wrongKey == null, "wrong password should be rejected");
    }

    private static void testEncryptionAtRest() throws Exception {
        File dir = newTempDir();
        SecretKeySpec key = CryptoUtil.deriveKey("pw".toCharArray(), CryptoUtil.generateSalt());
        VaultService vault = new VaultService(dir, key);
        vault.addItem("SuperSecretNetflixAccount", TrackedItem.Category.SUBSCRIPTION, LocalDate.now().plusDays(5),
                TrackedItem.RecurrenceType.MONTHLY, 0, 500, "shh", new ArrayList<>(), 0);

        byte[] raw = Files.readAllBytes(new File(dir, "items.dat").toPath());
        String rawAsText = new String(raw);
        assertTrue(!rawAsText.contains("SuperSecretNetflixAccount"), "encrypted file must not contain the plaintext item name");
    }

    private static void testRiskScoreOrdering() {
        TrackedItem overdueTax = new TrackedItem("A", "Tax filing", TrackedItem.Category.COMPLIANCE_TAX,
                LocalDate.now().minusDays(3), TrackedItem.RecurrenceType.YEARLY, 0, 0, "", new ArrayList<>(), 0);
        TrackedItem soonGrocery = new TrackedItem("B", "Milk", TrackedItem.Category.GROCERY,
                LocalDate.now().plusDays(1), TrackedItem.RecurrenceType.WEEKLY, 0, 0, "", new ArrayList<>(), 0);
        assertTrue(overdueTax.riskScore() > soonGrocery.riskScore(),
                "an overdue tax filing must outrank a soon-due low-stakes grocery item");

        TrackedItem insuranceIn10 = new TrackedItem("C", "Insurance", TrackedItem.Category.INSURANCE,
                LocalDate.now().plusDays(10), TrackedItem.RecurrenceType.YEARLY, 0, 0, "", new ArrayList<>(), 0);
        TrackedItem subscriptionIn9 = new TrackedItem("D", "Streaming", TrackedItem.Category.SUBSCRIPTION,
                LocalDate.now().plusDays(9), TrackedItem.RecurrenceType.MONTHLY, 0, 0, "", new ArrayList<>(), 0);
        assertTrue(insuranceIn10.riskScore() > subscriptionIn9.riskScore(),
                "category stakes should let insurance outrank a subscription due only one day sooner");
    }

    private static void testRecurrenceRollForward() {
        LocalDate base = LocalDate.of(2026, 1, 15);

        TrackedItem weekly = itemWithRecurrence(base, TrackedItem.RecurrenceType.WEEKLY, 0);
        weekly.rollToNextCycle();
        assertEquals(base.plusWeeks(1), weekly.getNextDueDate(), "weekly should roll forward 7 days");

        TrackedItem monthly = itemWithRecurrence(base, TrackedItem.RecurrenceType.MONTHLY, 0);
        monthly.rollToNextCycle();
        assertEquals(base.plusMonths(1), monthly.getNextDueDate(), "monthly should roll forward 1 month");

        TrackedItem yearly = itemWithRecurrence(base, TrackedItem.RecurrenceType.YEARLY, 0);
        yearly.rollToNextCycle();
        assertEquals(base.plusYears(1), yearly.getNextDueDate(), "yearly should roll forward 1 year");

        TrackedItem custom = itemWithRecurrence(base, TrackedItem.RecurrenceType.CUSTOM_DAYS, 45);
        custom.rollToNextCycle();
        assertEquals(base.plusDays(45), custom.getNextDueDate(), "custom-interval should roll forward by the configured days");

        TrackedItem oneTime = itemWithRecurrence(base, TrackedItem.RecurrenceType.ONE_TIME, 0);
        oneTime.rollToNextCycle();
        assertTrue(!oneTime.isActive(), "a one-time item should deactivate itself once its single deadline passes");
    }

    private static TrackedItem itemWithRecurrence(LocalDate due, TrackedItem.RecurrenceType type, int customDays) {
        return new TrackedItem("X", "Test item", TrackedItem.Category.OTHER, due, type, customDays, 0, "", new ArrayList<>(), 0);
    }

    private static void testHashChainIntegrity() {
        List<ReminderLog> chain = new ArrayList<>();
        ReminderLog l1 = new ReminderLog("LOG-0001", "ITEM-001", ReminderLog.EventType.ADDED, "created", null);
        chain.add(l1);
        ReminderLog l2 = new ReminderLog("LOG-0002", "ITEM-001", ReminderLog.EventType.RENEWED, "renewed", l1.getEntryHash());
        chain.add(l2);
        ReminderLog l3 = new ReminderLog("LOG-0003", "ITEM-001", ReminderLog.EventType.ALERT_SHOWN, "alerted", l2.getEntryHash());
        chain.add(l3);

        assertEquals(-1, ReminderEngine.verifyChain(chain), "a correctly chained log list should verify as intact");

        // Simulate tampering: splice in an entry whose previousHash doesn't match the entry before it.
        List<ReminderLog> tampered = new ArrayList<>(chain);
        ReminderLog forged = new ReminderLog("LOG-0002", "ITEM-001", ReminderLog.EventType.RENEWED,
                "renewed (forged)", "0000000000000000000000000000000000000000000000000000000000000000");
        tampered.set(1, forged);
        int brokenAt = ReminderEngine.verifyChain(tampered);
        assertEquals(2, brokenAt, "tampering with entry #2 should be caught at position 2");
    }

    private static void testCsvRoundTrip() throws Exception {
        File csvFile = File.createTempFile("renewalvault-test-", ".csv");
        csvFile.deleteOnExit();
        List<String> tags = new ArrayList<>(List.of("family", "essential"));
        TrackedItem original = new TrackedItem("ITEM-099", "Car, \"Insurance\"", TrackedItem.Category.INSURANCE,
                LocalDate.of(2026, 12, 1), TrackedItem.RecurrenceType.YEARLY, 0, 15000.5, "Renew before 1 Dec", tags, 21);

        ExportEngine exportEngine = new ExportEngine();
        exportEngine.exportCsv(List.of(original), csvFile);
        List<TrackedItem> reimported = exportEngine.importCsv(csvFile);

        assertEquals(1, reimported.size(), "should re-import exactly one item");
        TrackedItem back = reimported.get(0);
        assertEquals(original.getName(), back.getName(), "name (including embedded comma/quote) should survive the round trip");
        assertEquals(original.getCategory(), back.getCategory(), "category should survive the round trip");
        assertEquals(original.getNextDueDate(), back.getNextDueDate(), "due date should survive the round trip");
        assertEquals(original.getTags(), back.getTags(), "tags should survive the round trip");
        assertEquals(original.getAlertWindowDays(), back.getAlertWindowDays(), "alert window should survive the round trip");
    }

    private static void testUndo() throws Exception {
        File dir = newTempDir();
        SecretKeySpec key = CryptoUtil.deriveKey("pw".toCharArray(), CryptoUtil.generateSalt());
        VaultService vault = new VaultService(dir, key);

        vault.addItem("Temp Item", TrackedItem.Category.OTHER, LocalDate.now().plusDays(5),
                TrackedItem.RecurrenceType.ONE_TIME, 0, 0, "", new ArrayList<>(), 0);
        assertEquals(1, vault.listAll().size(), "item should be added");
        assertTrue(vault.hasUndo(), "an undo should be available after adding");
        vault.undoLast();
        assertEquals(0, vault.listAll().size(), "undo should remove the just-added item");
        assertTrue(!vault.hasUndo(), "undo stack should be empty after being used");

        TrackedItem item = vault.addItem("Another Item", TrackedItem.Category.OTHER, LocalDate.now().plusDays(5),
                TrackedItem.RecurrenceType.ONE_TIME, 0, 0, "", new ArrayList<>(), 0);
        vault.delete(item.getItemId());
        assertEquals(0, vault.listAll().size(), "item should be deleted");
        vault.undoLast();
        assertEquals(1, vault.listAll().size(), "undo should restore the deleted item");
    }

    private static void testBackupRestore() throws Exception {
        File sourceDir = newTempDir();
        VaultAuth auth = new VaultAuth(sourceDir);
        SecretKeySpec key = auth.setUpNewVault("backup-pw".toCharArray());
        VaultService vault = new VaultService(sourceDir, key);
        vault.addItem("Backup Test Item", TrackedItem.Category.WARRANTY, LocalDate.now().plusDays(30),
                TrackedItem.RecurrenceType.ONE_TIME, 0, 0, "", new ArrayList<>(), 0);

        File backupFile = File.createTempFile("renewalvault-test-", ".rvbackup");
        backupFile.deleteOnExit();
        BackupEngine backupEngine = new BackupEngine();
        backupEngine.exportBackup(sourceDir, backupFile);

        File restoredDir = newTempDir();
        backupEngine.restoreBackup(backupFile, restoredDir);

        VaultAuth restoredAuth = new VaultAuth(restoredDir);
        assertTrue(!restoredAuth.isFirstRun(), "restored dir should have a vault.meta");
        SecretKeySpec restoredKey = restoredAuth.unlock("backup-pw".toCharArray());
        assertTrue(restoredKey != null, "the original password should still unlock the restored backup");

        VaultService restoredVault = new VaultService(restoredDir, restoredKey);
        assertEquals(1, restoredVault.listAll().size(), "restored vault should contain the same item count");
        assertEquals("Backup Test Item", restoredVault.listAll().get(0).getName(), "restored item data should match");
    }

    private static void testDuplicateDetection() throws Exception {
        File dir = newTempDir();
        SecretKeySpec key = CryptoUtil.deriveKey("pw".toCharArray(), CryptoUtil.generateSalt());
        VaultService vault = new VaultService(dir, key);
        vault.addItem("Spotify Premium", TrackedItem.Category.SUBSCRIPTION, LocalDate.now().plusDays(10),
                TrackedItem.RecurrenceType.MONTHLY, 0, 199, "", new ArrayList<>(), 0);

        assertTrue(vault.findPossibleDuplicate("spotify premium").isPresent(),
                "duplicate check should be case-insensitive and find the existing item");
        assertTrue(vault.findPossibleDuplicate("Spotify Family").isEmpty(),
                "a genuinely different name should not be flagged as a duplicate");
    }

    private static void testRenewalStreak() throws Exception {
        File dir = newTempDir();
        SecretKeySpec key = CryptoUtil.deriveKey("pw".toCharArray(), CryptoUtil.generateSalt());
        VaultService vault = new VaultService(dir, key);
        ReminderEngine engine = new ReminderEngine(vault, dir, key);

        TrackedItem item = vault.addItem("Streak Test", TrackedItem.Category.SUBSCRIPTION, LocalDate.now().plusDays(3),
                TrackedItem.RecurrenceType.WEEKLY, 0, 0, "", new ArrayList<>(), 0);

        // Renewal #1: due in 3 days (not overdue) -> on time.
        engine.renewItem(item.getItemId());
        assertEquals(1, engine.currentOnTimeStreak(item.getItemId()), "first on-time renewal should start a streak of 1");

        // Push the item overdue, then renew late -> streak should reset to 0.
        vault.editItem(item.getItemId(), item.getName(), item.getCategory(), LocalDate.now().minusDays(2),
                item.getRecurrenceType(), item.getCustomIntervalDays(), item.getEstimatedCost(), item.getNotes(),
                item.getTags(), item.getAlertWindowDays());
        engine.renewItem(item.getItemId());
        assertEquals(0, engine.currentOnTimeStreak(item.getItemId()), "a late renewal should reset the streak to 0");

        // Renew again while on time -> streak restarts at 1.
        vault.editItem(item.getItemId(), item.getName(), item.getCategory(), LocalDate.now().plusDays(5),
                item.getRecurrenceType(), item.getCustomIntervalDays(), item.getEstimatedCost(), item.getNotes(),
                item.getTags(), item.getAlertWindowDays());
        engine.renewItem(item.getItemId());
        assertEquals(1, engine.currentOnTimeStreak(item.getItemId()), "a fresh on-time renewal should restart the streak");
    }

    private static void testPasswordRotation() throws Exception {
        File dir = newTempDir();
        VaultAuth auth = new VaultAuth(dir);
        SecretKeySpec oldKey = auth.setUpNewVault("old-password".toCharArray());
        VaultService vault = new VaultService(dir, oldKey);
        vault.addItem("Rotation Test Item", TrackedItem.Category.OTHER, LocalDate.now().plusDays(5),
                TrackedItem.RecurrenceType.ONE_TIME, 0, 0, "", new ArrayList<>(), 0);

        // Rotate: set up a new vault.meta (new salt/verifier) and re-encrypt items.dat with the new key.
        SecretKeySpec newKey = auth.setUpNewVault("new-password".toCharArray());
        FileStore.reencryptFile(new File(dir, "items.dat"), oldKey, newKey);

        // Old password must no longer unlock; new password must.
        assertTrue(auth.unlock("old-password".toCharArray()) == null, "old password should no longer unlock after rotation");
        SecretKeySpec reloadedKey = auth.unlock("new-password".toCharArray());
        assertTrue(reloadedKey != null, "new password should unlock after rotation");

        VaultService reloadedVault = new VaultService(dir, reloadedKey);
        assertEquals(1, reloadedVault.listAll().size(), "data should still be readable (and correct) after re-encryption");
        assertEquals("Rotation Test Item", reloadedVault.listAll().get(0).getName(), "item content should be unchanged after rotation");
    }
}
