package ti4.ai.trade;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import ti4.ai.brain.AiMemory;

record Draft(
        String partner,
        Purpose purpose,
        long startedAt,
        long lastPressAt,
        int restarts,
        boolean aborting,
        boolean opened,
        String window,
        Deal target) {

    static final String KEY = "tradeDraft";
    static final int MAX_RESTARTS = 1;
    private static final String FIELD = "~";
    private static final int FIELDS = 8;
    private static final String ABORTING = "abort";
    private static final String OPENED = "1";
    private static final String CLOSED = "0";

    static Draft fresh(String partner, Purpose purpose, long now, boolean opened, String window, Deal target) {
        return new Draft(partner, purpose, now, now, 0, false, opened, window, target);
    }

    static Optional<Draft> read(AiMemory memory) {
        return memory.get(KEY).flatMap(Draft::decode);
    }

    static void clear(AiMemory memory) {
        memory.remove(KEY);
    }

    void write(AiMemory memory) {
        TradeMemory.rewrite(memory, KEY, encode());
    }

    Draft pressedAt(long now) {
        return new Draft(partner, purpose, startedAt, now, restarts, aborting, opened, window, target);
    }

    Draft openedAt(long now) {
        return new Draft(partner, purpose, startedAt, now, restarts, aborting, true, window, target);
    }

    Draft restartedAt(long now) {
        return new Draft(partner, purpose, startedAt, now, restarts + 1, aborting, true, window, target);
    }

    Draft abortingAt(long now) {
        return new Draft(partner, purpose, startedAt, now, restarts, true, opened, window, target);
    }

    boolean restartsUsedUp() {
        return restarts >= MAX_RESTARTS;
    }

    String encode() {
        return String.join(
                FIELD,
                partner,
                purpose.name(),
                String.valueOf(startedAt),
                String.valueOf(lastPressAt),
                aborting ? ABORTING : String.valueOf(restarts),
                opened ? OPENED : CLOSED,
                window,
                target.encode());
    }

    static Optional<Draft> decode(String encoded) {
        String[] fields = encoded.split(FIELD, FIELDS);
        if (fields.length != FIELDS || fields[0].isEmpty()) return Optional.empty();
        Optional<Purpose> purpose = Purpose.named(fields[1]);
        OptionalLong startedAt = TradeMemory.parseLong(fields[2]);
        OptionalLong lastPressAt = TradeMemory.parseLong(fields[3]);
        boolean aborting = ABORTING.equals(fields[4]);
        OptionalInt restarts = aborting ? OptionalInt.of(0) : TradeMemory.parseInt(fields[4]);
        Optional<Deal> target = Deal.decode(fields[7]);
        if (purpose.isEmpty()
                || startedAt.isEmpty()
                || lastPressAt.isEmpty()
                || restarts.isEmpty()
                || target.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Draft(
                fields[0],
                purpose.get(),
                startedAt.getAsLong(),
                lastPressAt.getAsLong(),
                restarts.getAsInt(),
                aborting,
                OPENED.equals(fields[5]),
                fields[6],
                target.get()));
    }
}
