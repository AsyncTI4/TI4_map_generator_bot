package ti4.ai.trade;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.perception.PromptButton;

@UtilityClass
class TradeEntry {

    private static final String ENTRY = "transaction";
    private static final String ENTRY_LABEL = "Transaction";
    private static final String CARDS_INFO = "cardsInfo";
    private static final String ENTRY_KEY = "tradeEntry";
    private static final String USES_KEY = "tradeEntryUses|";
    private static final String REFRESH_KEY = "tradeEntryRefresh|";
    private static final String FIELD = ",";
    private static final int ENTRY_FIELDS = 3;
    private static final int MAX_USES = 2;
    private static final long REFRESH_WAIT_MILLIS = 20_000L;

    static void observe(AiTurnContext context) {
        newestVisible(context).ifPresent(match -> remember(context.memory(), match));
    }

    static Optional<AiDecision> open(AiTurnContext context) {
        Optional<Match> entry =
                newestUsable(context).or(() -> remembered(context).filter(match -> usable(context, match)));
        if (entry.isPresent()) return press(context, entry.get());
        OptionalLong asked = TradeMemory.time(context.memory(), refreshKey(context));
        if (asked.isPresent()) {
            long until = asked.getAsLong() + REFRESH_WAIT_MILLIS;
            if (context.now() >= until) return Optional.empty();
            return Optional.of(new AiDecision.Wait(until, "trade: waiting for the transaction button"));
        }
        Optional<Match> refresh = Prompts.first(
                        context.prompts().stream().filter(AiPrompt::isHidden).toList(),
                        button -> CARDS_INFO.equals(button.handlerId()))
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        refresh.ifPresent(ignored -> context.memory().put(refreshKey(context), String.valueOf(context.now())));
        return refresh.map(match -> match.press("trade: bring the transaction button back into view"));
    }

    static Optional<AiDecision> openRefreshed(AiTurnContext context) {
        if (!refreshing(context)) return Optional.empty();
        Optional<Match> fresh =
                newestUsable(context).filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        return fresh.flatMap(match -> press(context, match));
    }

    private static Optional<AiDecision> press(AiTurnContext context, Match entry) {
        TradeMemory.increment(context.memory(), USES_KEY + entry.prompt().messageId());
        return Optional.of(entry.press("trade: open the transaction buttons"));
    }

    private static boolean refreshing(AiTurnContext context) {
        OptionalLong asked = TradeMemory.time(context.memory(), refreshKey(context));
        return asked.isPresent() && context.now() < asked.getAsLong() + REFRESH_WAIT_MILLIS;
    }

    private static Optional<Match> newestVisible(AiTurnContext context) {
        return Prompts.first(Prompts.newestFirst(context.prompts()), TradeEntry::isEntry);
    }

    private static Optional<Match> newestUsable(AiTurnContext context) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            Optional<Match> entry = prompt.firstEnabled(TradeEntry::isEntry)
                    .map(button -> new Match(prompt, button))
                    .filter(match -> usable(context, match));
            if (entry.isPresent()) return entry;
        }
        return Optional.empty();
    }

    private static boolean isEntry(PromptButton button) {
        return button.isUnowned() && ENTRY.equals(button.handlerId());
    }

    private static boolean usable(AiTurnContext context, Match match) {
        return TradeMemory.count(context.memory(), USES_KEY + match.prompt().messageId()) < MAX_USES;
    }

    private static void remember(AiMemory memory, Match match) {
        String location = String.join(
                FIELD,
                match.prompt().channelId(),
                match.prompt().messageId(),
                match.button().customId());
        if (!location.equals(memory.get(ENTRY_KEY).orElse(""))) TradeMemory.rewrite(memory, ENTRY_KEY, location);
    }

    private static Optional<Match> remembered(AiTurnContext context) {
        String[] fields = StringUtils.splitPreserveAllTokens(
                context.memory().get(ENTRY_KEY).orElse(""), FIELD, ENTRY_FIELDS);
        if (fields == null || fields.length != ENTRY_FIELDS) return Optional.empty();
        PromptButton button = new PromptButton(0, fields[2], ENTRY, null, ENTRY_LABEL, false);
        AiPrompt prompt =
                new AiPrompt(fields[0], fields[1], PromptSource.AI_THREAD, "", List.of(button), context.now());
        return Optional.of(new Match(prompt, button));
    }

    private static String refreshKey(AiTurnContext context) {
        return REFRESH_KEY + context.turnKey();
    }
}
