package renewalvault.engine;

import renewalvault.db.FileStore;
import renewalvault.model.TrackedItem;

import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 1: Item Management.
 * Handles creation, lookup, updating, searching/filtering and
 * listing of tracked items (subscriptions, warranties, insurance,
 * licenses, domains, etc), plus a lightweight single-step undo.
 */
public class VaultService {

    public enum SortBy { RISK_SCORE, DUE_DATE, NAME, COST, CATEGORY }

    private final FileStore<TrackedItem> store;
    private final List<TrackedItem> items;
    private Runnable pendingUndo; // reverts the single most recent mutating action

    public VaultService(File dataDir, SecretKeySpec key) {
        this.store = new FileStore<>(dataDir, "items.dat", key);
        this.items = store.loadAll();
    }

    public TrackedItem addItem(String name, TrackedItem.Category category, LocalDate nextDueDate,
                                TrackedItem.RecurrenceType recurrenceType, int customIntervalDays,
                                double estimatedCost, String notes, List<String> tags, int alertWindowDays) {
        String id = "ITEM-" + String.format("%03d", nextSequenceNumber());
        TrackedItem item = new TrackedItem(id, name, category, nextDueDate, recurrenceType,
                customIntervalDays, estimatedCost, notes, tags, alertWindowDays);
        items.add(item);
        persist();
        pendingUndo = () -> { items.remove(item); persist(); };
        return item;
    }

    /** Case-insensitive check for an existing active item with the same name, to warn against accidental duplicates. */
    public Optional<TrackedItem> findPossibleDuplicate(String name) {
        return listActive().stream().filter(i -> i.getName().equalsIgnoreCase(name.trim())).findFirst();
    }

    /**
     * Updates every editable field on an existing item in place. Pass the item's current value for any
     * field that shouldn't change. Returns false if the item doesn't exist. Registers an undo that restores
     * every prior field value.
     */
    public boolean editItem(String itemId, String name, TrackedItem.Category category, LocalDate nextDueDate,
                             TrackedItem.RecurrenceType recurrenceType, int customIntervalDays,
                             double estimatedCost, String notes, List<String> tags, int alertWindowDays) {
        Optional<TrackedItem> found = findById(itemId);
        if (found.isEmpty()) return false;
        TrackedItem item = found.get();

        String prevName = item.getName();
        TrackedItem.Category prevCategory = item.getCategory();
        LocalDate prevDue = item.getNextDueDate();
        TrackedItem.RecurrenceType prevRecurrence = item.getRecurrenceType();
        int prevCustomDays = item.getCustomIntervalDays();
        double prevCost = item.getEstimatedCost();
        String prevNotes = item.getNotes();
        List<String> prevTags = new ArrayList<>(item.getTags());
        int prevAlertWindow = item.getAlertWindowDays();

        item.setName(name);
        item.setCategory(category);
        item.setNextDueDate(nextDueDate);
        item.setRecurrenceType(recurrenceType);
        item.setCustomIntervalDays(customIntervalDays);
        item.setEstimatedCost(estimatedCost);
        item.setNotes(notes);
        item.setTags(tags);
        item.setAlertWindowDays(alertWindowDays);
        persist();

        pendingUndo = () -> {
            item.setName(prevName);
            item.setCategory(prevCategory);
            item.setNextDueDate(prevDue);
            item.setRecurrenceType(prevRecurrence);
            item.setCustomIntervalDays(prevCustomDays);
            item.setEstimatedCost(prevCost);
            item.setNotes(prevNotes);
            item.setTags(prevTags);
            item.setAlertWindowDays(prevAlertWindow);
            persist();
        };
        return true;
    }

    private int nextSequenceNumber() {
        return items.stream()
                .map(TrackedItem::getItemId)
                .map(id -> id.replace("ITEM-", ""))
                .mapToInt(s -> { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } })
                .max().orElse(0) + 1;
    }

    /** Merges CSV-imported items into the live vault, assigning fresh IDs so nothing collides with existing items. */
    public List<TrackedItem> importItems(List<TrackedItem> imported) {
        List<TrackedItem> added = new ArrayList<>();
        for (TrackedItem src : imported) {
            String id = "ITEM-" + String.format("%03d", nextSequenceNumber());
            TrackedItem item = new TrackedItem(id, src.getName(), src.getCategory(), src.getNextDueDate(),
                    src.getRecurrenceType(), src.getCustomIntervalDays(), src.getEstimatedCost(),
                    src.getNotes(), new ArrayList<>(src.getTags()), src.getAlertWindowDays());
            item.setActive(src.isActive());
            items.add(item);
            added.add(item);
        }
        persist();
        return added;
    }

    public List<TrackedItem> listAll() {
        return items;
    }

    public List<TrackedItem> listActive() {
        return items.stream().filter(TrackedItem::isActive).collect(Collectors.toList());
    }

    public Optional<TrackedItem> findById(String itemId) {
        return items.stream().filter(i -> i.getItemId().equalsIgnoreCase(itemId)).findFirst();
    }

    public void persist() {
        store.saveAll(items);
    }

    public boolean deactivate(String itemId) {
        Optional<TrackedItem> found = findById(itemId);
        if (found.isPresent()) {
            TrackedItem item = found.get();
            boolean previousState = item.isActive();
            item.setActive(false);
            persist();
            pendingUndo = () -> { item.setActive(previousState); persist(); };
            return true;
        }
        return false;
    }

    public boolean delete(String itemId) {
        Optional<TrackedItem> found = findById(itemId);
        if (found.isPresent()) {
            TrackedItem item = found.get();
            int index = items.indexOf(item);
            items.remove(item);
            persist();
            pendingUndo = () -> { items.add(Math.min(index, items.size()), item); persist(); };
            return true;
        }
        return false;
    }

    /** Reverts the single most recent add/deactivate/delete/renew action, if one is available. */
    public boolean undoLast() {
        if (pendingUndo == null) return false;
        pendingUndo.run();
        pendingUndo = null;
        return true;
    }

    public boolean hasUndo() {
        return pendingUndo != null;
    }

    /** Lets a collaborating engine (e.g. ReminderEngine.renewItem) register the undo for its own action. */
    public void registerUndo(Runnable undo) {
        this.pendingUndo = undo;
    }

    // ---- Search / filter / sort: turns the flat list into whatever view the user actually needs ----

    public List<TrackedItem> search(String nameFragment) {
        String needle = nameFragment.toLowerCase(Locale.ROOT);
        return listActive().stream()
                .filter(i -> i.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || i.getTags().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(needle)))
                .collect(Collectors.toList());
    }

    public List<TrackedItem> filterByCategory(TrackedItem.Category category) {
        return listActive().stream().filter(i -> i.getCategory() == category).collect(Collectors.toList());
    }

    public List<TrackedItem> filterOverdue() {
        return listActive().stream().filter(TrackedItem::isOverdue).collect(Collectors.toList());
    }

    public List<TrackedItem> filterDueWithin(int days) {
        return listActive().stream().filter(i -> i.daysUntilDue() <= days).collect(Collectors.toList());
    }

    public List<TrackedItem> sorted(List<TrackedItem> source, SortBy sortBy) {
        Comparator<TrackedItem> comparator = switch (sortBy) {
            case RISK_SCORE -> Comparator.comparingDouble(TrackedItem::riskScore).reversed();
            case DUE_DATE -> Comparator.comparing(TrackedItem::getNextDueDate);
            case NAME -> Comparator.comparing(TrackedItem::getName, String.CASE_INSENSITIVE_ORDER);
            case COST -> Comparator.comparingDouble(TrackedItem::getEstimatedCost).reversed();
            case CATEGORY -> Comparator.comparing(TrackedItem::getCategory);
        };
        return source.stream().sorted(comparator).collect(Collectors.toList());
    }
}
