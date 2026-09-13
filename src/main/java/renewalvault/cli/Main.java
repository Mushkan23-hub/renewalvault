package renewalvault.cli;

import renewalvault.db.FileStore;
import renewalvault.engine.AnalyticsEngine;
import renewalvault.engine.BackupEngine;
import renewalvault.engine.ExportEngine;
import renewalvault.engine.ReminderEngine;
import renewalvault.engine.VaultService;
import renewalvault.model.ReminderLog;
import renewalvault.model.TrackedItem;
import renewalvault.security.VaultAuth;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Module 4: CLI & Reporting.
 * Menu-driven command-line entry point for RenewalVault. Wraps every
 * item mutation behind a master-password-derived AES key so the
 * on-disk vault is genuinely encrypted, then exposes the full set of
 * item management, urgency, analytics, and import/export features
 * through a single terminal menu.
 */
public class Main {

    private static final Scanner scanner = new Scanner(System.in);
    private static final File dataDir = new File("data");

    private static VaultService vaultService;
    private static ReminderEngine reminderEngine;
    private static AnalyticsEngine analyticsEngine;
    private static final ExportEngine exportEngine = new ExportEngine();
    private static final BackupEngine backupEngine = new BackupEngine();

    public static void main(String[] args) {
        System.out.println(ConsoleColors.BOLD + "========================================" + ConsoleColors.RESET);
        System.out.println(ConsoleColors.BOLD + "  RenewalVault - Never Miss a Deadline" + ConsoleColors.RESET);
        System.out.println("  (encrypted local vault + forensic audit trail)");
        System.out.println(ConsoleColors.BOLD + "========================================" + ConsoleColors.RESET);

        SecretKeySpec key = login();
        if (key == null) {
            System.out.println("Could not unlock the vault. Exiting.");
            return;
        }

        vaultService = new VaultService(dataDir, key);
        reminderEngine = new ReminderEngine(vaultService, dataDir, key);
        analyticsEngine = new AnalyticsEngine(vaultService, reminderEngine);

        showDashboard();
        runIntegrityCheckSilently();

        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> addItemFlow();
                case "2" -> editItemFlow();
                case "3" -> listAllItems();
                case "4" -> showDashboard();
                case "5" -> renewItemFlow();
                case "6" -> snoozeItemFlow();
                case "7" -> deactivateItemFlow();
                case "8" -> deleteItemFlow();
                case "9" -> undoFlow();
                case "10" -> searchFilterFlow();
                case "11" -> viewHistoryFlow();
                case "12" -> showCategoryOverview();
                case "13" -> analyticsFlow();
                case "14" -> exportFlow();
                case "15" -> importFlow();
                case "16" -> integrityCheckFlow();
                case "17" -> changePasswordFlow();
                case "18" -> {
                    vaultService.persist();
                    reminderEngine.persistLogs();
                    System.out.println("Goodbye! Your encrypted data is saved in ./data");
                    running = false;
                }
                default -> System.out.println("Invalid option. Please choose 1-18.");
            }
        }
    }

    // ---------------------------------------------------------------
    // Login / vault setup
    // ---------------------------------------------------------------

    private static SecretKeySpec login() {
        VaultAuth auth = new VaultAuth(dataDir);
        if (!dataDir.exists()) dataDir.mkdirs();

        try {
            if (auth.isFirstRun()) {
                System.out.println("\nNo vault found here.");
                System.out.println("1. Create a new vault");
                System.out.println("2. Restore from a .rvbackup file");
                System.out.print("Choose: ");
                String choice = scanner.nextLine().trim();

                if (choice.equals("2")) {
                    System.out.print("Path to .rvbackup file: ");
                    File backupFile = new File(scanner.nextLine().trim());
                    if (!backupFile.exists()) {
                        System.out.println("File not found: " + backupFile.getPath());
                        return null;
                    }
                    backupEngine.restoreBackup(backupFile, dataDir);
                    System.out.println("Backup restored. Enter the password that vault was created with.");
                    for (int attempt = 1; attempt <= 3; attempt++) {
                        char[] pw = VaultAuth.readPassword("Master password: ", scanner);
                        SecretKeySpec key = auth.unlock(pw);
                        if (key != null) {
                            System.out.println("Vault unlocked.\n");
                            return key;
                        }
                        System.out.println("Incorrect password (" + attempt + "/3).");
                    }
                    return null;
                }

                System.out.println("\nLet's set one up.");
                System.out.println("Choose a master password. It encrypts every file in ./data with AES-256 --");
                System.out.println("there is no recovery if you forget it, so pick something you'll remember.");
                char[] pw1 = VaultAuth.readPassword("New master password: ", scanner);
                char[] pw2 = VaultAuth.readPassword("Confirm master password: ", scanner);
                if (!java.util.Arrays.equals(pw1, pw2)) {
                    System.out.println("Passwords did not match.");
                    return null;
                }
                if (pw1.length < 4) {
                    System.out.println("Please choose at least 4 characters.");
                    return null;
                }
                SecretKeySpec key = auth.setUpNewVault(pw1);
                System.out.println("Vault created and encrypted.\n");
                return key;
            } else {
                for (int attempt = 1; attempt <= 3; attempt++) {
                    char[] pw = VaultAuth.readPassword("Master password: ", scanner);
                    SecretKeySpec key = auth.unlock(pw);
                    if (key != null) {
                        System.out.println("Vault unlocked.\n");
                        return key;
                    }
                    System.out.println("Incorrect password (" + attempt + "/3).");
                }
                return null;
            }
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("Vault error: " + e.getMessage());
            return null;
        }
    }

    private static void runIntegrityCheckSilently() {
        int brokenAt = reminderEngine.verifyAuditIntegrity();
        if (brokenAt != -1) {
            System.out.println(ConsoleColors.RED + "WARNING: the audit trail's hash chain is broken at entry #"
                    + brokenAt + " -- log history may have been tampered with outside the app." + ConsoleColors.RESET);
        }
    }

    // ---------------------------------------------------------------
    // Menu
    // ---------------------------------------------------------------

    private static void printMenu() {
        System.out.println("\n--- MENU ---");
        System.out.println(" 1. Add a tracked item");
        System.out.println(" 2. Edit an existing item");
        System.out.println(" 3. List all items");
        System.out.println(" 4. Show urgency dashboard");
        System.out.println(" 5. Mark item as renewed");
        System.out.println(" 6. Snooze an item's alerts");
        System.out.println(" 7. Deactivate an item");
        System.out.println(" 8. Delete an item permanently");
        System.out.println(" 9. Undo last action" + (vaultService.hasUndo() ? "" : " (nothing to undo)"));
        System.out.println("10. Search / filter / sort");
        System.out.println("11. View reminder history");
        System.out.println("12. Category overview (all life domains)");
        System.out.println("13. Analytics: budget, spend charts & streaks");
        System.out.println("14. Export (CSV / calendar .ics / full encrypted backup)");
        System.out.println("15. Import items from CSV");
        System.out.println("16. Verify audit trail integrity");
        System.out.println("17. Change master password");
        System.out.println("18. Save & exit");
        System.out.print("Choose an option: ");
    }

    // ---------------------------------------------------------------
    // Dashboard
    // ---------------------------------------------------------------

    private static void showDashboard() {
        System.out.println("\n=== UPCOMING DEADLINES (next 14 days + overdue, ranked by risk) ===");
        List<TrackedItem> alerts = reminderEngine.getUpcomingAlerts();
        if (alerts.isEmpty()) {
            System.out.println("Nothing due soon. You're all caught up.");
        } else {
            for (TrackedItem item : alerts) {
                System.out.println("  " + ConsoleColors.colorize(item));
            }
            double totalCost = reminderEngine.totalUpcomingCost(14);
            if (totalCost > 0) {
                System.out.printf("  Estimated upcoming cost (14 days): %.2f%n", totalCost);
            }
            reminderEngine.logAlertsShown(alerts);
        }
    }

    // ---------------------------------------------------------------
    // Add item
    // ---------------------------------------------------------------

    private static void addItemFlow() {
        try {
            System.out.print("Name (e.g. 'Car Insurance', 'Netflix'): ");
            String name = scanner.nextLine().trim();

            var duplicate = vaultService.findPossibleDuplicate(name);
            if (duplicate.isPresent()) {
                System.out.println(ConsoleColors.YELLOW + "Note: an active item named \"" + duplicate.get().getName()
                        + "\" (" + duplicate.get().getItemId() + ") already exists." + ConsoleColors.RESET);
                System.out.print("Add it anyway as a separate item? (y/n): ");
                if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
                    System.out.println("Cancelled.");
                    return;
                }
            }

            System.out.println("Category: 1=SUBSCRIPTION 2=INSURANCE 3=WARRANTY 4=LICENSE_ID 5=DOMAIN");
            System.out.println("          6=VEHICLE 7=HEALTH 8=GROCERY 9=COMPLIANCE_TAX 10=PET_PLANT_CARE 11=OTHER");
            System.out.print("Choose category number: ");
            TrackedItem.Category category = mapCategory(scanner.nextLine().trim());

            System.out.print("Next due date (YYYY-MM-DD): ");
            LocalDate dueDate = LocalDate.parse(scanner.nextLine().trim());

            System.out.println("Recurrence: 1=ONE_TIME 2=WEEKLY 3=MONTHLY 4=YEARLY 5=CUSTOM_DAYS");
            System.out.print("Choose recurrence number: ");
            TrackedItem.RecurrenceType recurrence = mapRecurrence(scanner.nextLine().trim());

            int customDays = 0;
            if (recurrence == TrackedItem.RecurrenceType.CUSTOM_DAYS) {
                System.out.print("Repeat every how many days? ");
                customDays = Integer.parseInt(scanner.nextLine().trim());
            }

            System.out.print("Estimated cost (0 if none): ");
            double cost = Double.parseDouble(scanner.nextLine().trim());

            System.out.print("Notes (optional): ");
            String notes = scanner.nextLine().trim();

            System.out.print("Tags, comma-separated (optional, e.g. 'family,essential'): ");
            String tagLine = scanner.nextLine().trim();
            List<String> tags = new ArrayList<>();
            if (!tagLine.isEmpty()) {
                for (String t : tagLine.split(",")) {
                    if (!t.isBlank()) tags.add(t.trim());
                }
            }

            System.out.print("Custom alert window in days before due date (0 = use default 14): ");
            String windowLine = scanner.nextLine().trim();
            int alertWindow = windowLine.isEmpty() ? 0 : Integer.parseInt(windowLine);

            TrackedItem item = vaultService.addItem(name, category, dueDate, recurrence, customDays,
                    cost, notes, tags, alertWindow);
            reminderEngine.logAdded(item);
            System.out.println("Added: " + item);

        } catch (DateTimeParseException e) {
            System.out.println("Invalid date format. Please use YYYY-MM-DD.");
        } catch (NumberFormatException e) {
            System.out.println("Invalid number entered. Please try again.");
        } catch (Exception e) {
            System.out.println("Something went wrong: " + e.getMessage());
        }
    }

    private static TrackedItem.Category mapCategory(String choice) {
        return switch (choice) {
            case "1" -> TrackedItem.Category.SUBSCRIPTION;
            case "2" -> TrackedItem.Category.INSURANCE;
            case "3" -> TrackedItem.Category.WARRANTY;
            case "4" -> TrackedItem.Category.LICENSE_ID;
            case "5" -> TrackedItem.Category.DOMAIN;
            case "6" -> TrackedItem.Category.VEHICLE;
            case "7" -> TrackedItem.Category.HEALTH;
            case "8" -> TrackedItem.Category.GROCERY;
            case "9" -> TrackedItem.Category.COMPLIANCE_TAX;
            case "10" -> TrackedItem.Category.PET_PLANT_CARE;
            default -> TrackedItem.Category.OTHER;
        };
    }

    private static TrackedItem.RecurrenceType mapRecurrence(String choice) {
        return switch (choice) {
            case "2" -> TrackedItem.RecurrenceType.WEEKLY;
            case "3" -> TrackedItem.RecurrenceType.MONTHLY;
            case "4" -> TrackedItem.RecurrenceType.YEARLY;
            case "5" -> TrackedItem.RecurrenceType.CUSTOM_DAYS;
            default -> TrackedItem.RecurrenceType.ONE_TIME;
        };
    }

    // ---------------------------------------------------------------
    // Edit item
    // ---------------------------------------------------------------

    private static void editItemFlow() {
        System.out.print("Enter item ID to edit: ");
        String id = scanner.nextLine().trim();
        var found = vaultService.findById(id);
        if (found.isEmpty()) {
            System.out.println("Item not found: " + id);
            return;
        }
        TrackedItem item = found.get();
        System.out.println("Current: " + item);
        System.out.println("Leave any field blank to keep its current value.\n");

        try {
            System.out.print("Name [" + item.getName() + "]: ");
            String nameInput = scanner.nextLine().trim();
            String name = nameInput.isEmpty() ? item.getName() : nameInput;

            System.out.println("Category: 1=SUBSCRIPTION 2=INSURANCE 3=WARRANTY 4=LICENSE_ID 5=DOMAIN");
            System.out.println("          6=VEHICLE 7=HEALTH 8=GROCERY 9=COMPLIANCE_TAX 10=PET_PLANT_CARE 11=OTHER");
            System.out.print("Category [" + item.getCategory() + "]: ");
            String categoryInput = scanner.nextLine().trim();
            TrackedItem.Category category = categoryInput.isEmpty() ? item.getCategory() : mapCategory(categoryInput);

            System.out.print("Next due date [" + item.getNextDueDate() + "] (YYYY-MM-DD): ");
            String dateInput = scanner.nextLine().trim();
            LocalDate dueDate = dateInput.isEmpty() ? item.getNextDueDate() : LocalDate.parse(dateInput);

            System.out.println("Recurrence: 1=ONE_TIME 2=WEEKLY 3=MONTHLY 4=YEARLY 5=CUSTOM_DAYS");
            System.out.print("Recurrence [" + item.getRecurrenceType() + "]: ");
            String recurrenceInput = scanner.nextLine().trim();
            TrackedItem.RecurrenceType recurrence = recurrenceInput.isEmpty() ? item.getRecurrenceType() : mapRecurrence(recurrenceInput);

            int customDays = item.getCustomIntervalDays();
            if (recurrence == TrackedItem.RecurrenceType.CUSTOM_DAYS) {
                System.out.print("Repeat every how many days [" + item.getCustomIntervalDays() + "]: ");
                String customInput = scanner.nextLine().trim();
                if (!customInput.isEmpty()) customDays = Integer.parseInt(customInput);
            }

            System.out.print("Estimated cost [" + item.getEstimatedCost() + "]: ");
            String costInput = scanner.nextLine().trim();
            double cost = costInput.isEmpty() ? item.getEstimatedCost() : Double.parseDouble(costInput);

            System.out.print("Notes [" + item.getNotes() + "]: ");
            String notesInput = scanner.nextLine().trim();
            String notes = notesInput.isEmpty() ? item.getNotes() : notesInput;

            System.out.print("Tags, comma-separated [" + String.join(",", item.getTags()) + "]: ");
            String tagInput = scanner.nextLine().trim();
            List<String> tags = item.getTags();
            if (!tagInput.isEmpty()) {
                tags = new ArrayList<>();
                for (String t : tagInput.split(",")) {
                    if (!t.isBlank()) tags.add(t.trim());
                }
            }

            System.out.print("Custom alert window in days [" + item.getAlertWindowDays() + "]: ");
            String windowInput = scanner.nextLine().trim();
            int alertWindow = windowInput.isEmpty() ? item.getAlertWindowDays() : Integer.parseInt(windowInput);

            vaultService.editItem(id, name, category, dueDate, recurrence, customDays, cost, notes, tags, alertWindow);
            reminderEngine.logEdited(id, "Fields updated via edit menu");
            System.out.println("Updated: " + vaultService.findById(id).orElseThrow());

        } catch (DateTimeParseException e) {
            System.out.println("Invalid date format. Please use YYYY-MM-DD. No changes were saved.");
        } catch (NumberFormatException e) {
            System.out.println("Invalid number entered. No changes were saved.");
        } catch (Exception e) {
            System.out.println("Something went wrong: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // List / renew / snooze / deactivate / delete / undo
    // ---------------------------------------------------------------

    private static void listAllItems() {
        System.out.println("\n=== ALL TRACKED ITEMS ===");
        List<TrackedItem> all = vaultService.listAll();
        if (all.isEmpty()) {
            System.out.println("No items tracked yet. Use option 1 to add one.");
            return;
        }
        for (TrackedItem item : all) {
            String line = item.isActive() ? ConsoleColors.colorize(item) : item + " [INACTIVE]";
            System.out.println("  " + line);
        }
    }

    private static void renewItemFlow() {
        System.out.print("Enter item ID to mark as renewed (e.g. ITEM-001): ");
        String id = scanner.nextLine().trim();
        if (reminderEngine.renewItem(id)) {
            System.out.println("Renewed. Next due date updated.");
        } else {
            System.out.println("Item not found: " + id);
        }
    }

    private static void snoozeItemFlow() {
        System.out.print("Enter item ID to snooze: ");
        String id = scanner.nextLine().trim();
        System.out.print("Snooze until (YYYY-MM-DD): ");
        try {
            LocalDate until = LocalDate.parse(scanner.nextLine().trim());
            if (reminderEngine.snoozeItem(id, until)) {
                System.out.println("Snoozed. It won't appear as due-soon until " + until + ".");
            } else {
                System.out.println("Item not found: " + id);
            }
        } catch (DateTimeParseException e) {
            System.out.println("Invalid date format. Please use YYYY-MM-DD.");
        }
    }

    private static void deactivateItemFlow() {
        System.out.print("Enter item ID to deactivate: ");
        String id = scanner.nextLine().trim();
        if (vaultService.deactivate(id)) {
            System.out.println("Item deactivated. (Use Undo if that was a mistake.)");
        } else {
            System.out.println("Item not found: " + id);
        }
    }

    private static void deleteItemFlow() {
        System.out.print("Enter item ID to permanently delete: ");
        String id = scanner.nextLine().trim();
        System.out.print("Are you sure? This removes it entirely (y/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("Cancelled.");
            return;
        }
        if (vaultService.delete(id)) {
            System.out.println("Item deleted. (Use Undo if that was a mistake.)");
        } else {
            System.out.println("Item not found: " + id);
        }
    }

    private static void undoFlow() {
        if (vaultService.undoLast()) {
            System.out.println("Last action undone.");
        } else {
            System.out.println("Nothing to undo.");
        }
    }

    // ---------------------------------------------------------------
    // Search / filter / sort
    // ---------------------------------------------------------------

    private static void searchFilterFlow() {
        System.out.println("\n1=Search by name/tag  2=Filter by category  3=Overdue only  4=Due within N days");
        System.out.print("Choose: ");
        String mode = scanner.nextLine().trim();
        List<TrackedItem> results;
        switch (mode) {
            case "1" -> {
                System.out.print("Search text: ");
                results = vaultService.search(scanner.nextLine().trim());
            }
            case "2" -> {
                System.out.print("Category number (1-11, see add-item menu): ");
                results = vaultService.filterByCategory(mapCategory(scanner.nextLine().trim()));
            }
            case "3" -> results = vaultService.filterOverdue();
            case "4" -> {
                System.out.print("Within how many days? ");
                try {
                    results = vaultService.filterDueWithin(Integer.parseInt(scanner.nextLine().trim()));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number.");
                    return;
                }
            }
            default -> {
                System.out.println("Invalid option.");
                return;
            }
        }

        System.out.println("Sort by: 1=Risk score (default) 2=Due date 3=Name 4=Cost 5=Category");
        System.out.print("Choose: ");
        VaultService.SortBy sortBy = switch (scanner.nextLine().trim()) {
            case "2" -> VaultService.SortBy.DUE_DATE;
            case "3" -> VaultService.SortBy.NAME;
            case "4" -> VaultService.SortBy.COST;
            case "5" -> VaultService.SortBy.CATEGORY;
            default -> VaultService.SortBy.RISK_SCORE;
        };
        results = vaultService.sorted(results, sortBy);

        System.out.println("\n=== RESULTS (" + results.size() + ") ===");
        if (results.isEmpty()) {
            System.out.println("No matching items.");
        }
        for (TrackedItem item : results) {
            System.out.println("  " + ConsoleColors.colorize(item));
        }
    }

    // ---------------------------------------------------------------
    // History / integrity
    // ---------------------------------------------------------------

    private static void viewHistoryFlow() {
        System.out.print("Enter item ID to view history (or leave blank for all): ");
        String id = scanner.nextLine().trim();
        List<ReminderLog> logs = id.isEmpty() ? reminderEngine.allLogs() : reminderEngine.historyFor(id);
        if (logs.isEmpty()) {
            System.out.println("No history found.");
            return;
        }
        System.out.println("\n=== REMINDER HISTORY (hash-chained, tamper-evident) ===");
        for (ReminderLog log : logs) {
            System.out.println("  " + log);
        }
    }

    private static void integrityCheckFlow() {
        int brokenAt = reminderEngine.verifyAuditIntegrity();
        if (brokenAt == -1) {
            System.out.println(ConsoleColors.GREEN
                    + "Audit trail intact: all " + reminderEngine.allLogs().size()
                    + " log entries are correctly hash-chained." + ConsoleColors.RESET);
        } else {
            System.out.println(ConsoleColors.RED
                    + "Audit trail COMPROMISED at entry #" + brokenAt
                    + " -- its hash doesn't match its content, or it doesn't chain from the previous entry."
                    + ConsoleColors.RESET);
        }
    }

    // ---------------------------------------------------------------
    // Category overview / analytics
    // ---------------------------------------------------------------

    private static void showCategoryOverview() {
        System.out.println("\n=== YOUR LIFE, ONE PLATFORM: CATEGORY OVERVIEW ===");
        System.out.print(analyticsEngine.renderCategoryCountChart());
    }

    private static void analyticsFlow() {
        System.out.println("\n=== ESTIMATED SPEND BY CATEGORY ===");
        System.out.print(analyticsEngine.renderCostChart());

        System.out.println("\n=== PROJECTED SPEND ===");
        System.out.printf("  Next 30 days : %.2f%n", analyticsEngine.projectedSpend(30));
        System.out.printf("  Next 90 days : %.2f%n", analyticsEngine.projectedSpend(90));
        System.out.printf("  Next 365 days: %.2f%n", analyticsEngine.projectedSpend(365));

        Map<String, Integer> streaks = reminderEngine.streaksForAllItems();
        if (!streaks.isEmpty()) {
            System.out.println("\n=== ON-TIME RENEWAL STREAKS ===");
            streaks.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> vaultService.findById(e.getKey()).ifPresent(item ->
                            System.out.printf("  %s %-25s %d renewal(s) in a row on time%n",
                                    e.getValue() >= 3 ? "\uD83D\uDD25" : "  ", item.getName(), e.getValue())));
        }
    }

    // ---------------------------------------------------------------
    // Export / import
    // ---------------------------------------------------------------

    private static void exportFlow() {
        System.out.println("1=Export CSV backup  2=Export calendar (.ics)  3=Export full encrypted backup (.rvbackup)");
        System.out.print("Choose: ");
        String choice = scanner.nextLine().trim();

        if (choice.equals("3")) {
            System.out.print("Output file name (e.g. 'vault.rvbackup'): ");
            File outFile = new File(scanner.nextLine().trim());
            try {
                backupEngine.exportBackup(dataDir, outFile);
                System.out.println("Encrypted backup written to " + outFile.getPath()
                        + " -- still requires your master password to restore. Safe to move to another machine or cloud storage.");
            } catch (IOException e) {
                System.out.println("Backup failed: " + e.getMessage());
            }
            return;
        }

        if (!choice.equals("1") && !choice.equals("2")) {
            System.out.println("Invalid option.");
            return;
        }
        System.out.println(ConsoleColors.YELLOW
                + "Note: unlike your vault, CSV and .ics exports are PLAIN TEXT once they leave RenewalVault --"
                + " keep the exported file somewhere you trust." + ConsoleColors.RESET);
        System.out.print("Output file name (e.g. 'backup.csv' or 'reminders.ics'): ");
        String fileName = scanner.nextLine().trim();
        File outFile = new File(fileName);
        try {
            if (choice.equals("1")) {
                exportEngine.exportCsv(vaultService.listAll(), outFile);
                System.out.println("Exported " + vaultService.listAll().size() + " item(s) to " + outFile.getPath());
            } else {
                exportEngine.exportIcs(vaultService.listActive(), outFile);
                System.out.println("Exported calendar to " + outFile.getPath()
                        + " -- import it into Google Calendar / Outlook / Apple Calendar.");
            }
        } catch (IOException e) {
            System.out.println("Export failed: " + e.getMessage());
        }
    }

    private static void importFlow() {
        System.out.print("CSV file to import (must match RenewalVault's export format): ");
        String fileName = scanner.nextLine().trim();
        File inFile = new File(fileName);
        if (!inFile.exists()) {
            System.out.println("File not found: " + fileName);
            return;
        }
        try {
            List<TrackedItem> parsed = exportEngine.importCsv(inFile);
            List<TrackedItem> added = vaultService.importItems(parsed);
            System.out.println("Imported " + added.size() + " item(s).");
        } catch (Exception e) {
            System.out.println("Import failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Master password rotation
    // ---------------------------------------------------------------

    private static void changePasswordFlow() {
        VaultAuth auth = new VaultAuth(dataDir);
        try {
            System.out.println("Changing your master password re-encrypts your entire vault. This cannot be undone.");
            char[] oldPw = VaultAuth.readPassword("Current master password: ", scanner);
            SecretKeySpec oldKey = auth.unlock(oldPw);
            if (oldKey == null) {
                System.out.println("Incorrect current password. Nothing was changed.");
                return;
            }

            char[] newPw1 = VaultAuth.readPassword("New master password: ", scanner);
            char[] newPw2 = VaultAuth.readPassword("Confirm new master password: ", scanner);
            if (!java.util.Arrays.equals(newPw1, newPw2)) {
                System.out.println("New passwords did not match. Nothing was changed.");
                return;
            }
            if (newPw1.length < 4) {
                System.out.println("Please choose at least 4 characters. Nothing was changed.");
                return;
            }

            // Flush the current in-memory state to disk under the OLD key before rotating.
            vaultService.persist();
            reminderEngine.persistLogs();

            SecretKeySpec newKey = auth.setUpNewVault(newPw1); // overwrites vault.meta with a fresh salt + verifier
            FileStore.reencryptFile(new File(dataDir, "items.dat"), oldKey, newKey);
            FileStore.reencryptFile(new File(dataDir, "logs.dat"), oldKey, newKey);

            // Reload every engine against the newly re-encrypted files to confirm the rotation actually worked.
            vaultService = new VaultService(dataDir, newKey);
            reminderEngine = new ReminderEngine(vaultService, dataDir, newKey);
            analyticsEngine = new AnalyticsEngine(vaultService, reminderEngine);

            System.out.println(ConsoleColors.GREEN + "Master password changed. Your vault has been fully re-encrypted."
                    + ConsoleColors.RESET);
        } catch (GeneralSecurityException | IOException e) {
            System.out.println("Password change failed, and no changes were made: " + e.getMessage());
        }
    }
}
