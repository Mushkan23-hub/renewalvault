package renewalvault.engine;

import renewalvault.model.TrackedItem;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 5: Analytics.
 * Turns the flat item list into "one platform" insight: where your
 * money is committed across categories, and a rough estimate of what
 * the next 30/90/365 days will cost you if every recurring item is
 * renewed on schedule. Rendered as simple ASCII bar charts so it
 * stays true to the project's zero-dependency, terminal-only scope.
 */
public class AnalyticsEngine {

    private final VaultService vaultService;
    private final ReminderEngine reminderEngine;

    public AnalyticsEngine(VaultService vaultService, ReminderEngine reminderEngine) {
        this.vaultService = vaultService;
        this.reminderEngine = reminderEngine;
    }

    /** Projects total estimated spend if every active recurring item renews on schedule within the horizon. */
    public double projectedSpend(int horizonDays) {
        LocalDate cutoff = LocalDate.now().plusDays(horizonDays);
        double total = 0;
        for (TrackedItem item : vaultService.listActive()) {
            LocalDate cursor = item.getNextDueDate();
            int guard = 0;
            while (!cursor.isAfter(cutoff) && guard < 500) {
                total += item.getEstimatedCost();
                if (item.getRecurrenceType() == TrackedItem.RecurrenceType.ONE_TIME) break;
                cursor = switch (item.getRecurrenceType()) {
                    case WEEKLY -> cursor.plusWeeks(1);
                    case MONTHLY -> cursor.plusMonths(1);
                    case YEARLY -> cursor.plusYears(1);
                    case CUSTOM_DAYS -> cursor.plusDays(Math.max(item.getCustomIntervalDays(), 1));
                    case ONE_TIME -> cursor.plusYears(100); // unreachable, loop already breaks above
                };
                guard++;
            }
        }
        return total;
    }

    /** Renders a simple horizontal ASCII bar chart of estimated cost per category. */
    public String renderCostChart() {
        Map<TrackedItem.Category, Double> costs = reminderEngine.costByCategory();
        if (costs.isEmpty()) return "No active items with cost data yet.";

        double max = costs.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (max <= 0) max = 1.0;
        int barWidth = 30;

        StringBuilder sb = new StringBuilder();
        List<Map.Entry<TrackedItem.Category, Double>> sorted = costs.entrySet().stream()
                .sorted(Map.Entry.<TrackedItem.Category, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
        for (Map.Entry<TrackedItem.Category, Double> entry : sorted) {
            int filled = (int) Math.round((entry.getValue() / max) * barWidth);
            String bar = "#".repeat(Math.max(filled, entry.getValue() > 0 ? 1 : 0));
            sb.append(String.format("  %-16s %-30s %.2f%n", entry.getKey(), bar, entry.getValue()));
        }
        return sb.toString();
    }

    /** Renders a simple ASCII count chart of items per category (the "unified platform" view). */
    public String renderCategoryCountChart() {
        Map<TrackedItem.Category, Long> counts = reminderEngine.countByCategory();
        if (counts.isEmpty()) return "No items tracked yet across any category.";

        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        int barWidth = 30;

        StringBuilder sb = new StringBuilder();
        for (TrackedItem.Category category : TrackedItem.Category.values()) {
            long count = counts.getOrDefault(category, 0L);
            if (count == 0) continue;
            int filled = (int) Math.round(((double) count / max) * barWidth);
            String bar = "#".repeat(Math.max(filled, 1));
            sb.append(String.format("  %-16s %-30s %d%n", category, bar, count));
        }
        return sb.toString();
    }
}
