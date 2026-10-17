package ti4.ai.trade;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiMemory;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;
import ti4.game.Player;

record Pending(int offerNumber, long sentAt, Purpose purpose, long expiresAt, String openKey, String rescindTarget) {

    private static final String KEY = "tradePending|";
    private static final String FIELD = "~";
    private static final int FIELDS = 6;
    private static final String TARGET_FIELD = ",";
    private static final int TARGET_FIELDS = 3;
    private static final String RESCIND_LABEL = "Rescind Offer";

    static Optional<Pending> read(AiMemory memory, Player partner) {
        return memory.get(key(partner)).flatMap(Pending::decode);
    }

    static void drop(AiMemory memory, Player partner) {
        memory.remove(key(partner));
    }

    void write(AiMemory memory, Player partner) {
        TradeMemory.rewrite(memory, key(partner), encode());
    }

    Pending withRescindTarget(AiPrompt prompt, PromptButton button) {
        String target = String.join(TARGET_FIELD, prompt.channelId(), prompt.messageId(), button.customId());
        return new Pending(offerNumber, sentAt, purpose, expiresAt, openKey, target);
    }

    Optional<AiPrompt> rememberedRescind(String handlerId, long now) {
        String[] fields = StringUtils.splitPreserveAllTokens(rescindTarget, TARGET_FIELD, TARGET_FIELDS);
        if (fields == null || fields.length != TARGET_FIELDS) return Optional.empty();
        PromptButton button = new PromptButton(0, fields[2], handlerId, null, RESCIND_LABEL, false);
        return Optional.of(new AiPrompt(fields[0], fields[1], PromptSource.AI_THREAD, "", List.of(button), now));
    }

    private static String key(Player partner) {
        return KEY + partner.getFaction();
    }

    private String encode() {
        return String.join(
                FIELD,
                String.valueOf(offerNumber),
                String.valueOf(sentAt),
                purpose.name(),
                String.valueOf(expiresAt),
                openKey,
                rescindTarget);
    }

    private static Optional<Pending> decode(String encoded) {
        String[] fields = encoded.split(FIELD, FIELDS);
        if (fields.length != FIELDS) return Optional.empty();
        OptionalInt offerNumber = TradeMemory.parseInt(fields[0]);
        OptionalLong sentAt = TradeMemory.parseLong(fields[1]);
        Optional<Purpose> purpose = Purpose.named(fields[2]);
        OptionalLong expiresAt = TradeMemory.parseLong(fields[3]);
        if (offerNumber.isEmpty() || sentAt.isEmpty() || purpose.isEmpty() || expiresAt.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Pending(
                offerNumber.getAsInt(),
                sentAt.getAsLong(),
                purpose.get(),
                expiresAt.getAsLong(),
                fields[4],
                fields[5]));
    }
}
