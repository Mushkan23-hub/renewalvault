// ReminderEngine: risk-weighted urgency queue, hash-chained audit log, and renewal streak tracking.
package renewalvault.engine;

import renewalvault.db.FileStore;
import renewalvault.model.ReminderLog;
import renewalvault.model.TrackedItem;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;

/**
 * Module 2: The Reminder Engine -- the algorithmic core of the app.
 *
 * Rather than just listing items with dates, this maintains a
 * priority queue ordered by a weighted risk score (urgency, category
 * stakes, and cost combined -- see {@link TrackedItem#riskScore()}),
 * so an overdue tax filing correctly outranks a subscription that
 * happens to be due one day sooner. It generates a ranked "what
 * needs your attention" view, logs every alert/renewal/snooze into a
 * hash-chained, tamper-evident audit trail, and handles the
 * recurrence roll-forward logic when an item is renewed.
 */
public class ReminderEngine {

    private final VaultService vaultService;
    private final FileStore<ReminderLog> logStore;
    private final List<ReminderLog> logs;
    private static final int DEFAULT_ALERT_WINDOW_DAYS = 14;

    public ReminderEngine(VaultService vaultService, File dataDir, SecretKeySpec key) {
        this.vaultService = vaultService;
        this.logStore = new FileStore<>(dataDir, "logs.dat", key);
        this.logs = logStore.loadAll();
    }

    /**
     * Builds a priority queue of active, non-snoozed items ordered by
     * risk score (most urgent first).
     */
    public PriorityQueue<TrackedItem> buildUrgencyQueue() {
        PriorityQueue<TrackedItem> pq = new PriorityQueue<>(
                Comparator.comparingDouble(TrackedItem::riskScore).reversed());
        pq.addAll(vaultService.listActive().stream().filter(i -> !i.isSnoozed()).collect(Collectors.toList()));
        return pq;
    }

    /** Items due within the given alert window (or already overdue), most urgent first, excluding snoozed items. */
    public List<TrackedItem> getUpcomingAlerts(int windowDays) {
        PriorityQueue<TrackedItem> pq = buildUrgencyQueue();
        return pq.stream()
                .filter(i -> i.daysUntilDue() <= windowDays)
                .sorted(Comparator.comparingDouble(TrackedItem::riskScore).reversed())
                .collect(Collectors.toList());
    }

    public List<TrackedItem> getUpcomingAlerts() {
        return getUpcomingAlerts(DEFAULT_ALERT_WINDOW_DAYS);
    }

    public void logAlertsShown(List<TrackedItem> shown) {
        for (TrackedItem item : shown) {
            appendLog(item.getItemId(), ReminderLog.EventType.ALERT_SHOWN,
                    "Due in " + item.daysUntilDue() + " day(s), risk=" + Math.round(item.riskScore()));
        }
        logStore.saveAll(logs);
    }

    public void logAdded(TrackedItem item) {
        appendLog(item.getItemId(), ReminderLog.EventType.ADDED, "Created, due " + item.getNextDueDate());
        logStore.saveAll(logs);
    }

    /** Marks an item as renewed: rolls its due date forward per its recurrence rule and logs the event. */
    public boolean renewItem(String itemId) {
        return vaultService.findById(itemId).map(item -> {
            LocalDate previousDue = item.getNextDueDate();
            boolean previousActive = item.isActive();
            int punctuality = (int) item.daysUntilDue(); // >=0 renewed on/before due date, negative = renewed after it (late)
            item.rollToNextCycle();
            vaultService.persist();
            String note = punctuality >= 0
                    ? "Rolled to " + item.getNextDueDate() + " (renewed " + punctuality + "d before due)"
                    : "Rolled to " + item.getNextDueDate() + " (renewed " + (-punctuality) + "d LATE)";
            appendLog(itemId, ReminderLog.EventType.RENEWED, note, punctuality);
            logStore.saveAll(logs);
            vaultService.registerUndo(() -> {
                item.setNextDueDate(previousDue);
                item.setActive(previousActive);
                vaultService.persist();
            });
            return true;
        }).orElse(false);
    }

    /** Suppresses alerts for an item until the given date, without changing its actual due date. */
    public boolean snoozeItem(String itemId, LocalDate until) {
        return vaultService.findById(itemId).map(item -> {
            item.setSnoozedUntil(until);
            vaultService.persist();
            appendLog(itemId, ReminderLog.EventType.SNOOZED, "Snoozed until " + until);
            logStore.saveAll(logs);
            return true;
        }).orElse(false);
    }

    private void appendLog(String itemId, ReminderLog.EventType type, String note) {
        appendLog(itemId, type, note, null);
    }

    private void appendLog(String itemId, ReminderLog.EventType type, String note, Integer daysEarlyOrLate) {
        String previousHash = logs.isEmpty() ? null : logs.get(logs.size() - 1).getEntryHash();
        logs.add(new ReminderLog("LOG-" + String.format("%04d", logs.size() + 1), itemId, type, note,
                previousHash, daysEarlyOrLate));
    }

    public void logEdited(String itemId, String note) {
        appendLog(itemId, ReminderLog.EventType.EDITED, note);
        logStore.saveAll(logs);
    }

    /** Forces the in-memory log list to be written to disk, e.g. right before a master-password change. */
    public void persistLogs() {
        logStore.saveAll(logs);
    }

    public List<ReminderLog> historyFor(String itemId) {
        return logs.stream()
                .filter(l -> l.getItemId().equalsIgnoreCase(itemId))
                .collect(Collectors.toList());
    }

    public List<ReminderLog> allLogs() {
        return logs;
    }

    /**
     * Walks the hash chain and confirms every entry's stored hash still
     * matches what its content actually hashes to, and that each entry
     * correctly references the previous entry's hash. Returns the index
     * (1-based log position) of the first broken link, or -1 if the
     * entire audit trail is intact. This is the same tamper-detection
     * principle used to verify chain-of-custody in digital forensics,
     * applied here to a household reminders app.
     */
    public int verifyAuditIntegrity() {
        return verifyChain(logs);
    }

    /**
     * Pure, stateless chain-walk so the tamper-detection logic can be unit tested directly against an
     * arbitrary list of log entries, independent of any live ReminderEngine instance or file I/O.
     */
    public static int verifyChain(List<ReminderLog> chain) {
        String expectedPrevious = null;
        for (int i = 0; i < chain.size(); i++) {
            ReminderLog entry = chain.get(i);
            boolean linkOk = i == 0
                    ? "GENESIS".equals(entry.getPreviousHash())
                    : entry.getPreviousHash().equals(expectedPrevious);
            boolean contentOk = entry.getEntryHash().equals(entry.recomputeHash());
            if (!linkOk || !contentOk) {
                return i + 1;
            }
            expectedPrevious = entry.getEntryHash();
        }
        return -1;
    }

    /**
     * Walks an item's RENEWED history from most recent backwards and counts
     * how many in a row were on time. A single late renewal breaks the
     * streak. Returns 0 if the item has never been renewed or its most
     * recent renewal was late.
     */
    public int currentOnTimeStreak(String itemId) {
        List<ReminderLog> renewals = historyFor(itemId).stream()
                .filter(l -> l.getEventType() == ReminderLog.EventType.RENEWED)
                .collect(Collectors.toList());
        int streak = 0;
        for (int i = renewals.size() - 1; i >= 0; i--) {
            if (renewals.get(i).wasOnTime()) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    /** The item(s) currently on the longest on-time renewal streak, for a quick "you're doing great at X" callout. */
    public Map<String, Integer> streaksForAllItems() {
        return vaultService.listAll().stream()
                .collect(Collectors.toMap(TrackedItem::getItemId, i -> currentOnTimeStreak(i.getItemId())))
                .entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Total estimated upcoming cost across all active items due within the window -- a simple budgeting aid. */
    public double totalUpcomingCost(int windowDays) {
        return getUpcomingAlerts(windowDays).stream().mapToDouble(TrackedItem::getEstimatedCost).sum();
    }

    /** Groups all active items by category and counts them -- gives the unified "one platform" overview. */
    public Map<TrackedItem.Category, Long> countByCategory() {
        return vaultService.listActive().stream()
                .collect(Collectors.groupingBy(TrackedItem::getCategory, Collectors.counting()));
    }

    /** Sums estimated cost per category across all active items -- feeds the analytics/budget view. */
    public Map<TrackedItem.Category, Double> costByCategory() {
        return vaultService.listActive().stream()
                .collect(Collectors.groupingBy(TrackedItem::getCategory,
                        Collectors.summingDouble(TrackedItem::getEstimatedCost)));
    }
}

