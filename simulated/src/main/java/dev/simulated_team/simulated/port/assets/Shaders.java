package dev.simulated_team.simulated.port.assets;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PORT-26.3: small text edits for deriving 26.3 shaders from the official ones. Each edit checks that the text it
 * expects is there, so a changed official file fails loudly instead of producing a broken shader.
 */
final class Shaders {
    private Shaders() {}

    static String require(final String s, final String part) {
        if (!s.contains(part)) throw new IllegalStateException("official shader has no '" + part + "'");
        return s;
    }

    /** Removes each line whose trimmed text equals one of {@code lines}; every one must be present exactly once. */
    static String dropLines(final String s, final String... lines) {
        final Set<String> wanted = new HashSet<>(Arrays.asList(lines));
        final Set<String> seen = new HashSet<>();
        final String out = s.lines().filter(l -> {
            final String t = l.trim();
            if (wanted.contains(t)) {
                if (!seen.add(t)) throw new IllegalStateException("official shader has '" + t + "' twice");
                return false;
            }
            return true;
        }).collect(Collectors.joining("\n", "", "\n"));
        if (seen.size() != wanted.size()) {
            final Set<String> missing = new HashSet<>(wanted);
            missing.removeAll(seen);
            throw new IllegalStateException("official shader lacks " + missing);
        }
        return out;
    }

    /** Removes every line starting with {@code prefix} (after indentation); at least one must be present. */
    static String dropLinesStarting(final String s, final String prefix) {
        final int[] count = {0};
        final String out = s.lines().filter(l -> {
            if (l.trim().startsWith(prefix)) {
                count[0]++;
                return false;
            }
            return true;
        }).collect(Collectors.joining("\n", "", "\n"));
        if (count[0] == 0) throw new IllegalStateException("official shader has no line starting '" + prefix + "'");
        return out;
    }

    /**
     * Rewrites each call {@code prefix ARGS)} as {@code newPrefix ARGS newSuffix}, matching the call's closing
     * parenthesis; exactly {@code expected} calls must be found.
     */
    static String replaceCalls(final String s, final String prefix, final String newPrefix, final String newSuffix, final int expected) {
        final StringBuilder out = new StringBuilder();
        int from = 0, found = 0;
        for (int at = s.indexOf(prefix); at >= 0; at = s.indexOf(prefix, from)) {
            int depth = 1, i = at + prefix.length();
            for (; i < s.length() && depth > 0; i++) {
                if (s.charAt(i) == '(') depth++;
                else if (s.charAt(i) == ')') depth--;
            }
            if (depth != 0) throw new IllegalStateException("unbalanced call to '" + prefix + "'");
            out.append(s, from, at).append(newPrefix).append(s, at + prefix.length(), i - 1).append(newSuffix);
            from = i;
            found++;
        }
        out.append(s.substring(from));
        if (found != expected) throw new IllegalStateException("expected " + expected + " calls to '" + prefix + "', found " + found);
        return out.toString();
    }

    /** Replaces the single occurrence of {@code part}. */
    static String replaceOnce(final String s, final String part, final String with) {
        final int at = require(s, part).indexOf(part);
        if (s.indexOf(part, at + 1) >= 0) throw new IllegalStateException("official shader has '" + part + "' more than once");
        return s.substring(0, at) + with + s.substring(at + part.length());
    }

    /** The text between the braces of {@code void main()}. */
    static String mainBody(final byte[] src) {
        final String s = new String(src, java.nio.charset.StandardCharsets.UTF_8);
        final java.util.regex.Matcher m = java.util.regex.Pattern.compile("void\\s+main\\s*\\(\\s*\\)\\s*\\{").matcher(s);
        if (!m.find()) throw new IllegalStateException("no main() in the official shader");
        int depth = 1, i = m.end();
        for (; i < s.length() && depth > 0; i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') depth--;
        }
        return s.substring(m.end(), i - 1);
    }
}
