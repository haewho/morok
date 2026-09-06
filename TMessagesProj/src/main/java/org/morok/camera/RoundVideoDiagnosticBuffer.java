package org.morok.camera;

import java.util.ArrayList;

/** Pure bounded formatter for metadata-only camera diagnostics. */
public final class RoundVideoDiagnosticBuffer {
    public static final int MAX_EVENTS = 40;
    public static final int MAX_STORED_CHARACTERS = 32 * 1024;
    private static final int MAX_STAGE_CHARACTERS = 48;
    private static final int MAX_DETAIL_CHARACTERS = 600;

    private RoundVideoDiagnosticBuffer() {}

    public static String append(String stored, long timestamp, String stage, String detail) {
        ArrayList<String> events = parse(stored);
        events.add(Math.max(0, timestamp) + "\t" + clean(stage, MAX_STAGE_CHARACTERS)
                + "\t" + clean(detail, MAX_DETAIL_CHARACTERS));
        while (events.size() > MAX_EVENTS || joinedLength(events) > MAX_STORED_CHARACTERS) events.remove(0);
        return join(events);
    }

    public static int count(String stored) {
        return parse(stored).size();
    }

    public static String render(String stored) {
        ArrayList<String> events = parse(stored);
        StringBuilder output = new StringBuilder();
        for (String event : events) {
            if (output.length() > 0) output.append('\n');
            output.append(event.replace('\t', ' '));
        }
        return output.toString();
    }

    private static ArrayList<String> parse(String stored) {
        ArrayList<String> events = new ArrayList<>();
        if (stored == null || stored.isEmpty() || stored.length() > MAX_STORED_CHARACTERS) return events;
        for (String line : stored.split("\n")) {
            int first = line.indexOf('\t'), second = first < 0 ? -1 : line.indexOf('\t', first + 1);
            if (first <= 0 || second <= first + 1 || line.length() > 700) continue;
            boolean timestamp = true;
            for (int i = 0; i < first; i++) if (!Character.isDigit(line.charAt(i))) { timestamp = false; break; }
            if (timestamp) events.add(line);
        }
        while (events.size() > MAX_EVENTS) events.remove(0);
        return events;
    }

    private static String clean(String value, int limit) {
        if (value == null) return "unknown";
        StringBuilder result = new StringBuilder(Math.min(value.length(), limit));
        boolean previousSpace = false;
        for (int i = 0; i < value.length() && result.length() < limit; i++) {
            char character = value.charAt(i);
            boolean space = Character.isWhitespace(character) || Character.isISOControl(character);
            if (space) {
                if (!previousSpace && result.length() > 0) result.append(' ');
            } else {
                result.append(character);
            }
            previousSpace = space;
        }
        String cleaned = result.toString().trim();
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    private static int joinedLength(ArrayList<String> events) {
        int length = Math.max(0, events.size() - 1);
        for (String event : events) length += event.length();
        return length;
    }

    private static String join(ArrayList<String> events) {
        StringBuilder output = new StringBuilder(joinedLength(events));
        for (String event : events) {
            if (output.length() > 0) output.append('\n');
            output.append(event);
        }
        return output.toString();
    }
}
