package renewalvault.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one thing that needs attention by a deadline: a
 * subscription renewal, an insurance policy, a warranty, an ID or
 * license, a vehicle service date, a health checkup, a perishable
 * grocery item, a tax/compliance filing, a pet or plant care task,
 * etc. RenewalVault is a single unified platform for every kind of
 * "don't forget this by this date" need in a person's life, all
 * driven by the same urgency engine. Supports both one-time
 * deadlines (e.g. a warranty ending on a fixed date) and recurring
 * deadlines (e.g. a monthly subscription or a weekly pet-care task).
 */
public class TrackedItem implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Category {
        SUBSCRIPTION, INSURANCE, WARRANTY, LICENSE_ID, DOMAIN,
        VEHICLE, HEALTH, GROCERY, COMPLIANCE_TAX, PET_PLANT_CARE, OTHER;

        /**
         * Baseline risk weight used by the urgency engine. Categories where
         * missing the deadline is expensive, dangerous, or legally binding
         * (insurance lapsing, tax filings, vehicle compliance, health) are
         * weighted higher than low-stakes ones (groceries, subscriptions),
         * so the dashboard doesn't just sort by date -- it sorts by what
         * actually matters most if ignored.
         */
        public double riskWeight() {
            return switch (this) {
                case COMPLIANCE_TAX -> 2.0;
                case INSURANCE -> 1.8;
                case VEHICLE -> 1.6;
                case HEALTH -> 1.5;
                case LICENSE_ID -> 1.4;
                case WARRANTY -> 1.1;
                case DOMAIN -> 1.1;
                case PET_PLANT_CARE -> 1.05;
                case SUBSCRIPTION -> 1.0;
                case GROCERY -> 0.9;
                case OTHER -> 1.0;
            };
        }
    }

    public enum RecurrenceType { ONE_TIME, WEEKLY, MONTHLY, YEARLY, CUSTOM_DAYS }

    private final String itemId;
    private String name;
    private Category category;
    private LocalDate nextDueDate;
    private RecurrenceType recurrenceType;
    private int customIntervalDays; // only used when recurrenceType == CUSTOM_DAYS
    private double estimatedCost;   // optional, 0 if not tracked
    private String notes;
    private boolean active;
    private List<String> tags = new ArrayList<>();
    private int alertWindowDays;    // per-item override of the default alert window (0 = use global default)
    private LocalDate snoozedUntil; // if set and in the future, suppresses alerts until this date

    public TrackedItem(String itemId, String name, Category category, LocalDate nextDueDate,
                        RecurrenceType recurrenceType, int customIntervalDays,
                        double estimatedCost, String notes) {
        this(itemId, name, category, nextDueDate, recurrenceType, customIntervalDays,
                estimatedCost, notes, new ArrayList<>(), 0);
    }

    public TrackedItem(String itemId, String name, Category category, LocalDate nextDueDate,
                        RecurrenceType recurrenceType, int customIntervalDays,
                        double estimatedCost, String notes, List<String> tags, int alertWindowDays) {
        this.itemId = itemId;
        this.name = name;
        this.category = category;
        this.nextDueDate = nextDueDate;
        this.recurrenceType = recurrenceType;
        this.customIntervalDays = customIntervalDays;
        this.estimatedCost = estimatedCost;
        this.notes = notes;
        this.active = true;
        this.tags = tags == null ? new ArrayList<>() : tags;
        this.alertWindowDays = alertWindowDays;
    }

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate d) { this.nextDueDate = d; }
    public RecurrenceType getRecurrenceType() { return recurrenceType; }
    public void setRecurrenceType(RecurrenceType r) { this.recurrenceType = r; }
    public int getCustomIntervalDays() { return customIntervalDays; }
    public void setCustomIntervalDays(int d) { this.customIntervalDays = d; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double c) { this.estimatedCost = c; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { this.notes = n; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public int getAlertWindowDays() { return alertWindowDays; }
    public void setAlertWindowDays(int d) { this.alertWindowDays = d; }
    public LocalDate getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(LocalDate d) { this.snoozedUntil = d; }

    public long daysUntilDue() {
        return ChronoUnit.DAYS.between(LocalDate.now(), nextDueDate);
    }

    public boolean isOverdue() {
        return daysUntilDue() < 0;
    }

    public boolean isSnoozed() {
        return snoozedUntil != null && !snoozedUntil.isBefore(LocalDate.now());
    }

    /**
     * A single, comparable risk score combining urgency (days remaining),
     * category stakes, and cost -- this is what actually drives the
     * dashboard ordering, not just the raw date. Higher = more urgent.
     */
    public double riskScore() {
        long days = daysUntilDue();
        double urgencyComponent = days < 0
                ? 1000 + (Math.abs(days) * 25.0)   // overdue items dominate, and get worse the longer they sit
                : Math.max(0, 300 - (days * 6.0));  // decays as due date recedes, floors at 0 past ~50 days out
        double costComponent = Math.min(estimatedCost / 20.0, 100.0); // diminishing returns, capped
        return (urgencyComponent * category.riskWeight()) + costComponent;
    }

    /** Advances nextDueDate forward by one recurrence cycle (called after the deadline is acknowledged/renewed). */
    public void rollToNextCycle() {
        switch (recurrenceType) {
            case WEEKLY -> nextDueDate = nextDueDate.plusWeeks(1);
            case MONTHLY -> nextDueDate = nextDueDate.plusMonths(1);
            case YEARLY -> nextDueDate = nextDueDate.plusYears(1);
            case CUSTOM_DAYS -> nextDueDate = nextDueDate.plusDays(customIntervalDays);
            case ONE_TIME -> active = false; // one-time items are done after their single deadline
        }
        this.snoozedUntil = null;
    }

    private String urgencyLabel() {
        if (isOverdue()) return "OVERDUE";
        if (isSnoozed()) return "SNOOZED";
        return daysUntilDue() <= effectiveAlertWindow() ? "DUE SOON" : "OK";
    }

    public int effectiveAlertWindow() {
        return alertWindowDays > 0 ? alertWindowDays : 14;
    }

    @Override
    public String toString() {
        String tagStr = tags.isEmpty() ? "" : " #" + String.join(" #", tags);
        return String.format("[%s] %-25s | %-14s | Due: %s (%+d days) | %-9s | %-9s | risk=%.0f%s",
                itemId, name, category, nextDueDate, daysUntilDue(), urgencyLabel(),
                recurrenceType == RecurrenceType.ONE_TIME ? "one-time" : "recurring",
                riskScore(), tagStr);
    }
}
