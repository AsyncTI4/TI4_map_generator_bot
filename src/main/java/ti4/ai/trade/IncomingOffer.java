package ti4.ai.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;
import ti4.game.Player;

record IncomingOffer(
        AiPrompt prompt, PromptButton accept, PromptButton reject, PromptButton counter, String color, String number) {

    private static final String ACCEPT = "acceptOffer_";
    private static final String REJECT = "rejectOffer_";
    private static final String SEEN_KEY = "offerSeen|";
    private static final String ANSWERED = "answered";
    private static final String STALE = "stale";
    private static final String BLACK_MARKET = "_BMD_";
    private static final String SEPARATOR = "_";
    private static final int ACCEPT_FIELDS = 2;
    private static final String OFFER_NUMBER_KEY = "offerFrom";
    private static final String OFFER_NUMBER_TO = "To";

    static List<IncomingOffer> visible(AiTurnContext context) {
        List<IncomingOffer> offers = new ArrayList<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden()) of(prompt).ifPresent(offers::add);
        }
        return offers;
    }

    static List<IncomingOffer> live(AiTurnContext context) {
        return visible(context).stream().filter(offer -> offer.isLive(context)).toList();
    }

    static boolean liveFrom(AiTurnContext context, Player offerer) {
        return live(context).stream().anyMatch(offer -> offer.isFrom(context.game(), offerer));
    }

    static String offerNumberKey(Player offerer, Player receiver) {
        return OFFER_NUMBER_KEY + offerer.getFaction() + OFFER_NUMBER_TO + receiver.getFaction();
    }

    private static Optional<IncomingOffer> of(AiPrompt prompt) {
        Optional<PromptButton> accept = prompt.firstEnabled(
                button -> button.isUnowned() && button.handlerId().startsWith(ACCEPT));
        if (accept.isEmpty()) return Optional.empty();
        String[] fields = plain(accept.get()).substring(ACCEPT.length()).split(SEPARATOR);
        if (fields.length != ACCEPT_FIELDS || !StringUtils.isNumeric(fields[1])) return Optional.empty();
        String color = fields[0];
        Optional<PromptButton> reject = unownedPlain(prompt, REJECT + color);
        Optional<PromptButton> counter = unownedPlain(prompt, BuilderPrompts.RESET + color);
        if (reject.isEmpty() || counter.isEmpty()) return Optional.empty();
        return Optional.of(new IncomingOffer(prompt, accept.get(), reject.get(), counter.get(), color, fields[1]));
    }

    private static Optional<PromptButton> unownedPlain(AiPrompt prompt, String handlerId) {
        return prompt.firstEnabled(button -> button.isUnowned() && handlerId.equals(plain(button)));
    }

    private static String plain(PromptButton button) {
        return StringUtils.substringBefore(button.handlerId(), BLACK_MARKET);
    }

    Optional<Player> offerer(Game game) {
        return Optional.ofNullable(game.getPlayerFromColorOrFaction(color));
    }

    boolean isFrom(Game game, Player player) {
        return offerer(game).filter(offerer -> Standings.same(offerer, player)).isPresent();
    }

    boolean isCurrent(Game game, Player seat) {
        Optional<Player> offerer = offerer(game);
        return offerer.isPresent()
                && number.equals(game.getStoredValue(offerNumberKey(offerer.get(), seat)))
                && !deal(offerer.get(), seat).isEmpty();
    }

    Deal deal(Player offerer, Player seat) {
        return Deal.between(offerer, seat);
    }

    boolean isLive(AiTurnContext context) {
        return untouched(context) && !closed(context);
    }

    boolean untouched(AiTurnContext context) {
        return !context.alreadyPressed(prompt, accept)
                && !context.alreadyPressed(prompt, reject)
                && !context.alreadyPressed(prompt, counter);
    }

    boolean closed(AiTurnContext context) {
        String seen = seen(context);
        return ANSWERED.equals(seen) || STALE.equals(seen);
    }

    String seen(AiTurnContext context) {
        return context.memory().get(seenKey()).orElse("");
    }

    void markSeen(AiTurnContext context, String fingerprint) {
        context.memory().put(seenKey(), fingerprint);
    }

    void markAnswered(AiTurnContext context) {
        TradeMemory.rewrite(context.memory(), seenKey(), ANSWERED);
    }

    void markStale(AiTurnContext context) {
        TradeMemory.rewrite(context.memory(), seenKey(), STALE);
    }

    private String seenKey() {
        return SEEN_KEY + prompt.messageId();
    }
}
