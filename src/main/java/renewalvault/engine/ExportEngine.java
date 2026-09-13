package renewalvault.engine;

import renewalvault.model.TrackedItem;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Module 6: Import / Export.
 *
 * Two genuinely useful escape hatches that make RenewalVault work
 * *with* the tools people already use instead of trying to replace
 * them:
 *
 * <ul>
 *   <li><b>CSV backup/restore</b> -- a human-readable snapshot you can
 *   open in a spreadsheet, edit in bulk, or hand to someone else.</li>
 *   <li><b>.ics calendar export</b> -- every due date, exported as a
 *   standard iCalendar file that Google Calendar / Outlook / Apple
 *   Calendar can import directly, so the vault's own alert window
 *   isn't the only way to get reminded. This stays within the
 *   project's "no external service dependency" scope: RenewalVault
 *   never talks to a calendar API, it just writes a plain .ics file
 *   the user can import wherever they like.</li>
 * </ul>
 */
public class ExportEngine {

    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String CSV_HEADER =
            "itemId,name,category,nextDueDate,recurrenceType,customIntervalDays,estimatedCost,notes,tags,alertWindowDays,active";

    public void exportCsv(List<TrackedItem> items, File outFile) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(outFile))) {
            w.write(CSV_HEADER);
            w.newLine();
            for (TrackedItem i : items) {
                w.write(String.join(",",
                        i.getItemId(),
                        csvEscape(i.getName()),
                        i.getCategory().name(),
                        i.getNextDueDate().toString(),
                        i.getRecurrenceType().name(),
                        String.valueOf(i.getCustomIntervalDays()),
                        String.valueOf(i.getEstimatedCost()),
                        csvEscape(i.getNotes() == null ? "" : i.getNotes()),
                        csvEscape(String.join(";", i.getTags())),
                        String.valueOf(i.getAlertWindowDays()),
                        String.valueOf(i.isActive())));
                w.newLine();
            }
        }
    }

    /** Parses a previously exported CSV back into TrackedItem objects (does not touch the live vault itself). */
    public List<TrackedItem> importCsv(File inFile) throws IOException {
        List<TrackedItem> result = new ArrayList<>();
        List<String> lines = Files.readAllLines(inFile.toPath());
        for (int lineNo = 1; lineNo < lines.size(); lineNo++) { // skip header
            String line = lines.get(lineNo).trim();
            if (line.isEmpty()) continue;
            String[] f = splitCsvLine(line);
            if (f.length < 11) continue;
            TrackedItem item = new TrackedItem(
                    f[0],
                    f[1],
                    TrackedItem.Category.valueOf(f[2]),
                    LocalDate.parse(f[3]),
                    TrackedItem.RecurrenceType.valueOf(f[4]),
                    Integer.parseInt(f[5]),
                    Double.parseDouble(f[6]),
                    f[7],
                    f[8].isBlank() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(f[8].split(";"))),
                    Integer.parseInt(f[9]));
            item.setActive(Boolean.parseBoolean(f[10]));
            result.add(item);
        }
        return result;
    }

    /** Writes a standards-compliant .ics file so any calendar app can pick up every due date directly. */
    public void exportIcs(List<TrackedItem> items, File outFile) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//RenewalVault//EN\r\n");
        for (TrackedItem item : items) {
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(item.getItemId()).append("@renewalvault\r\n");
            sb.append("DTSTART;VALUE=DATE:").append(item.getNextDueDate().format(ICS_DATE)).append("\r\n");
            sb.append("SUMMARY:").append(icsEscape(item.getName())).append(" (").append(item.getCategory()).append(")\r\n");
            if (item.getNotes() != null && !item.getNotes().isBlank()) {
                sb.append("DESCRIPTION:").append(icsEscape(item.getNotes())).append("\r\n");
            }
            String rrule = switch (item.getRecurrenceType()) {
                case WEEKLY -> "RRULE:FREQ=WEEKLY\r\n";
                case MONTHLY -> "RRULE:FREQ=MONTHLY\r\n";
                case YEARLY -> "RRULE:FREQ=YEARLY\r\n";
                default -> ""; // ONE_TIME and CUSTOM_DAYS: exported as a single dated event, not a recurrence rule
            };
            sb.append(rrule);
            sb.append("END:VEVENT\r\n");
        }
        sb.append("END:VCALENDAR\r\n");
        Files.writeString(outFile.toPath(), sb.toString());
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String icsEscape(String value) {
        return value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;");
    }

    /** Minimal CSV splitter that respects quoted fields containing commas (no external CSV library allowed). */
    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
