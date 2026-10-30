package ti4.ai.fallback;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

public record FallbackRecord(
        long atMillis,
        String game,
        int round,
        String phase,
        String faction,
        String color,
        String kind,
        String reason,
        String promptMessageId,
        boolean privatePrompt,
        String promptText,
        List<String> buttonIds,
        List<String> recentDecisions,
        String pendingDecision,
        String outcome) {

    private static final String FIELD = String.valueOf((char) 31);
    private static final String ITEM = String.valueOf((char) 30);
    private static final int FIELDS = 15;

    public String encode() {
        String joined = String.join(
                FIELD,
                String.valueOf(atMillis),
                clean(game),
                String.valueOf(round),
                clean(phase),
                clean(faction),
                clean(color),
                clean(kind),
                clean(reason),
                clean(promptMessageId),
                String.valueOf(privatePrompt),
                clean(promptText),
                String.join(ITEM, buttonIds.stream().map(FallbackRecord::clean).toList()),
                String.join(
                        ITEM,
                        recentDecisions.stream().map(FallbackRecord::clean).toList()),
                clean(pendingDecision),
                clean(outcome));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(joined.getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<FallbackRecord> decode(String encoded) {
        if (StringUtils.isBlank(encoded)) return Optional.empty();
        try {
            String joined = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] fields = StringUtils.splitPreserveAllTokens(joined, FIELD);
            if (fields.length != FIELDS) return Optional.empty();
            return Optional.of(new FallbackRecord(
                    Long.parseLong(fields[0]),
                    fields[1],
                    Integer.parseInt(fields[2]),
                    fields[3],
                    fields[4],
                    fields[5],
                    fields[6],
                    fields[7],
                    fields[8],
                    Boolean.parseBoolean(fields[9]),
                    fields[10],
                    items(fields[11]),
                    items(fields[12]),
                    fields[13],
                    fields[14]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public String logLine() {
        return FallbackLog.LINE_PREFIX + "game=" + game + " round=" + round + " phase=" + phase + " seat=" + faction
                + "/" + color + " kind=" + kind + " reason=\"" + reason + "\" prompt=" + promptMessageId
                + (privatePrompt ? " (private)" : "") + " text=\"" + promptText + "\" buttons=" + buttonIds
                + " recent=" + recentDecisions + " pending=\"" + pendingDecision + "\" outcome=\"" + outcome + "\"";
    }

    private static List<String> items(String joined) {
        List<String> items = new ArrayList<>();
        if (joined.isEmpty()) return items;
        for (String item : StringUtils.splitPreserveAllTokens(joined, ITEM)) items.add(item);
        return items;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace(FIELD, " ").replace(ITEM, " ").replace("\n", " ");
    }
}
