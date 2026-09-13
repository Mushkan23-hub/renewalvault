package renewalvault.cli;

import renewalvault.model.TrackedItem;

/**
 * Tiny ANSI color helper so the urgency dashboard is scannable at a
 * glance (red = overdue, yellow = due soon, cyan = snoozed, green =
 * fine) without pulling in any terminal UI library. Degrades
 * harmlessly to plain text on terminals that don't support ANSI
 * (the codes are simply invisible/ignored, not garbage characters,
 * on virtually every modern terminal including Windows Terminal,
 * VS Code, and any Unix shell).
 */
public final class ConsoleColors {
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String YELLOW = "\u001B[33m";
    public static final String GREEN = "\u001B[32m";
    public static final String CYAN = "\u001B[36m";
    public static final String BOLD = "\u001B[1m";

    private ConsoleColors() { }

    public static String colorize(TrackedItem item) {
        String color;
        if (item.isOverdue()) color = RED;
        else if (item.isSnoozed()) color = CYAN;
        else if (item.daysUntilDue() <= item.effectiveAlertWindow()) color = YELLOW;
        else color = GREEN;
        return color + item.toString() + RESET;
    }
}
