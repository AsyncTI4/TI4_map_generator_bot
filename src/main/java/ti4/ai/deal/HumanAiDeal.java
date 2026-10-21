package ti4.ai.deal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public record HumanAiDeal(
        String id,
        String aiFaction,
        String humanFaction,
        String purpose,
        int round,
        long expiresAt,
        Status status,
        String channelId,
        String messageId,
        List<Step> steps) {

    public enum Status {
        PENDING,
        ACCEPTED,
        DENIED,
        EXPIRED,
        FAILED
    }

    public enum Item {
        COMMODITIES("Comms"),
        TRADE_GOODS("TGs"),
        DEBT("SendDebt"),
        PROMISSORY("PNs");

        private final String token;

        Item(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    public sealed interface Step {

        record ExhaustLeader(String leaderId, String targetFaction, int minNewCommodities, String description)
                implements Step {}

        record Transfer(String fromFaction, String toFaction, Item item, int amount, String detail) implements Step {}

        static Step exhaust(String leaderId, String targetFaction, int minNewCommodities, String description) {
            return new ExhaustLeader(leaderId, targetFaction, minNewCommodities, clean(description));
        }

        static Step transfer(String fromFaction, String toFaction, Item item, int amount) {
            return new Transfer(fromFaction, toFaction, item, amount, "");
        }

        static Step note(String fromFaction, String toFaction, String noteId) {
            return new Transfer(fromFaction, toFaction, Item.PROMISSORY, 1, clean(noteId));
        }
    }

    private static final Pattern SEPARATORS = Pattern.compile("[|;,]");
    private static final String FIELD = "|";
    private static final String STEP = ";";
    private static final String PART = ",";
    private static final String EXHAUST = "X";
    private static final String TRANSFER = "T";
    private static final int FIELDS = 10;

    public HumanAiDeal {
        steps = List.copyOf(steps);
    }

    public HumanAiDeal withStatus(Status next) {
        return new HumanAiDeal(
                id, aiFaction, humanFaction, purpose, round, expiresAt, next, channelId, messageId, steps);
    }

    public HumanAiDeal withMessage(String channel, String message) {
        return new HumanAiDeal(id, aiFaction, humanFaction, purpose, round, expiresAt, status, channel, message, steps);
    }

    public String encode() {
        List<String> encodedSteps = new ArrayList<>();
        for (Step step : steps) {
            encodedSteps.add(
                    switch (step) {
                        case Step.ExhaustLeader exhaust ->
                            String.join(
                                    PART,
                                    EXHAUST,
                                    exhaust.leaderId(),
                                    exhaust.targetFaction(),
                                    String.valueOf(exhaust.minNewCommodities()),
                                    exhaust.description());
                        case Step.Transfer transfer ->
                            String.join(
                                    PART,
                                    TRANSFER,
                                    transfer.fromFaction(),
                                    transfer.toFaction(),
                                    transfer.item().name(),
                                    String.valueOf(transfer.amount()),
                                    transfer.detail());
                    });
        }
        return String.join(
                FIELD,
                id,
                aiFaction,
                humanFaction,
                clean(purpose),
                String.valueOf(round),
                String.valueOf(expiresAt),
                status.name(),
                StringUtils.defaultString(channelId),
                StringUtils.defaultString(messageId),
                String.join(STEP, encodedSteps));
    }

    public static Optional<HumanAiDeal> decode(String encoded) {
        if (StringUtils.isBlank(encoded)) return Optional.empty();
        String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
        if (fields.length != FIELDS) return Optional.empty();
        try {
            List<Step> steps = new ArrayList<>();
            for (String raw : StringUtils.split(fields[9], STEP)) {
                String[] parts = StringUtils.splitPreserveAllTokens(raw, PART);
                if (EXHAUST.equals(parts[0])) {
                    steps.add(new Step.ExhaustLeader(parts[1], parts[2], Integer.parseInt(parts[3]), parts[4]));
                } else {
                    steps.add(new Step.Transfer(
                            parts[1], parts[2], Item.valueOf(parts[3]), Integer.parseInt(parts[4]), parts[5]));
                }
            }
            return Optional.of(new HumanAiDeal(
                    fields[0],
                    fields[1],
                    fields[2],
                    fields[3],
                    Integer.parseInt(fields[4]),
                    Long.parseLong(fields[5]),
                    Status.valueOf(fields[6]),
                    fields[7],
                    fields[8],
                    steps));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String clean(String text) {
        return SEPARATORS.matcher(StringUtils.defaultString(text)).replaceAll(" ");
    }
}
