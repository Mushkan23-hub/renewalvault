package renewalvault.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * An immutable audit entry recording that a reminder was shown for a
 * TrackedItem, or that the user acknowledged/renewed/snoozed it.
 * Doubles as the app's history log for the "which deadlines have I
 * already handled" question.
 *
 * <p>Forensic touch: every entry stores a SHA-256 hash of its own
 * content chained to the hash of the entry before it (identical in
 * spirit to a blockchain / Git commit chain). Changing, deleting, or
 * reordering a past log entry breaks the chain, so {@link
 * renewalvault.engine.ReminderEngine#verifyAuditIntegrity()} can prove
 * whether the history file has been silently edited outside the app --
 * a small piece of applied digital-forensics thinking bolted onto an
 * everyday productivity tool.
 */
public class ReminderLog implements Serializable {
    private static final long serialVersionUID = 3L;

    public enum EventType { ALERT_SHOWN, RENEWED, SNOOZED, DEACTIVATED, ADDED, EDITED }

    private final String logId;
    private final String itemId;
    private final EventType eventType;
    private final LocalDateTime timestamp;
    private final String note;
    private final String previousHash; // hash of the prior entry in the chain ("GENESIS" for the first)
    private final String entryHash;    // sha256(previousHash + logId + itemId + eventType + timestamp + note + punctuality)
    private final Integer daysEarlyOrLate; // RENEWED entries only: >=0 = renewed that many days before/on the due date, <0 = renewed late; null otherwise

    public ReminderLog(String logId, String itemId, EventType eventType, String note, String previousHash) {
        this(logId, itemId, eventType, note, previousHash, null);
    }

    public ReminderLog(String logId, String itemId, EventType eventType, String note, String previousHash,
                        Integer daysEarlyOrLate) {
        this.logId = logId;
        this.itemId = itemId;
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.note = note;
        this.previousHash = previousHash == null ? "GENESIS" : previousHash;
        this.daysEarlyOrLate = daysEarlyOrLate;
        this.entryHash = computeHash(this.previousHash, logId, itemId, eventType, timestamp, note, daysEarlyOrLate);
    }

    public String getLogId() { return logId; }
    public String getItemId() { return itemId; }
    public EventType getEventType() { return eventType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getNote() { return note; }
    public String getPreviousHash() { return previousHash; }
    public String getEntryHash() { return entryHash; }
    public Integer getDaysEarlyOrLate() { return daysEarlyOrLate; }
    public boolean wasOnTime() { return daysEarlyOrLate != null && daysEarlyOrLate >= 0; }

    /** Recomputes the hash this entry *should* have from its own stored fields, to detect in-place tampering. */
    public String recomputeHash() {
        return computeHash(previousHash, logId, itemId, eventType, timestamp, note, daysEarlyOrLate);
    }

    public static String computeHash(String previousHash, String logId, String itemId, EventType eventType,
                                      LocalDateTime timestamp, String note, Integer daysEarlyOrLate) {
        String payload = previousHash + "|" + logId + "|" + itemId + "|" + eventType + "|" + timestamp
                + "|" + note + "|" + daysEarlyOrLate;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    @Override
    public String toString() {
        String punctuality = daysEarlyOrLate == null ? ""
                : (daysEarlyOrLate >= 0 ? " (on time, " + daysEarlyOrLate + "d early)" : " (LATE by " + (-daysEarlyOrLate) + "d)");
        return String.format("%s | %-11s | Item: %-8s | %s%s  [hash %s]",
                timestamp, eventType, itemId, note == null ? "" : note, punctuality, entryHash.substring(0, 10));
    }
}
