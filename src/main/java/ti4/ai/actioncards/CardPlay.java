package ti4.ai.actioncards;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;

record CardPlay(
        Stage stage,
        long since,
        long playedAt,
        String alias,
        String announcementId,
        boolean windowExpected,
        int playsBefore,
        String extra) {

    private static final String FIELD = "~";
    private static final int FIELDS = 8;

    enum Stage {
        MENU,
        PRESET,
        PLAYED,
        WINDOW,
        RESOLVE,
        TARGET,
        DONE,
        CANCELED
    }

    boolean active() {
        return stage != Stage.DONE && stage != Stage.CANCELED;
    }

    CardPlay at(Stage next, long now) {
        return new CardPlay(next, now, playedAt, alias, announcementId, windowExpected, playsBefore, extra);
    }

    CardPlay played(long now, int plays, boolean expectsWindow) {
        return new CardPlay(Stage.PLAYED, now, now, alias, announcementId, expectsWindow, plays, extra);
    }

    CardPlay withAnnouncement(String messageId) {
        return new CardPlay(stage, since, playedAt, alias, messageId, windowExpected, playsBefore, extra);
    }

    CardPlay withPlayedAt(long at) {
        return new CardPlay(stage, since, at, alias, announcementId, windowExpected, playsBefore, extra);
    }

    CardPlay withExtra(String value) {
        return new CardPlay(stage, since, playedAt, alias, announcementId, windowExpected, playsBefore, value);
    }

    String encode() {
        return String.join(
                FIELD,
                stage.name(),
                String.valueOf(since),
                String.valueOf(playedAt),
                alias,
                announcementId,
                windowExpected ? "1" : "0",
                String.valueOf(playsBefore),
                extra);
    }

    static Optional<CardPlay> decode(String encoded) {
        String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
        if (fields == null || fields.length != FIELDS) return Optional.empty();
        if (!StringUtils.isNumeric(fields[1])
                || !StringUtils.isNumeric(fields[2])
                || !StringUtils.isNumeric(fields[6])) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CardPlay(
                    Stage.valueOf(fields[0]),
                    Long.parseLong(fields[1]),
                    Long.parseLong(fields[2]),
                    fields[3],
                    fields[4],
                    "1".equals(fields[5]),
                    Integer.parseInt(fields[6]),
                    fields[7]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
