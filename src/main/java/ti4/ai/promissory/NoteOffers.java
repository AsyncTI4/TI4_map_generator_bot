package ti4.ai.promissory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.ActionCardRules;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.strategy.ResearchPolicy;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class NoteOffers {

    private static final String TRADE_AGREEMENT_OFFER = "useTA_";
    private static final String TRADE_AGREEMENT = "_ta";
    private static final String RESEARCH_AGREEMENT = "ra";
    private static final String RESEARCH_AGREEMENT_OFFER = "resolvePNPlay_ra_";
    private static final String MILITARY_SUPPORT = "ms";
    private static final String MILITARY_SUPPORT_OFFER = "resolvePNPlay_ms";
    private static final String MILITARY_SUPPORT_TARGET = "placeOneNDone_skipbuild_2gf_";
    private static final String MILITARY_SUPPORT_KEY = "militarySupportPlayedAt";
    private static final String ACTION_SUMMARY = "currentActionSummary";
    private static final String GIFT_OF_PRESCIENCE = "gift";
    private static final String GIFT_PRESET = "resolvePreassignment_Play Naalu PN";
    private static final String GIFT_PRESET_STORED = "Play Naalu PN";
    private static final String DECLINE = "deleteButtons";
    private static final String STRATEGY_PHASE = "strategy";
    private static final double RESEARCH_AGREEMENT_MIN_VALUE = 2.0;
    private static final long OFFER_WINDOW_MILLIS = 10 * 60_000L;
    private static final long PLACEMENT_WAIT_MILLIS = 30 * 60_000L;
    private static final long CLOCK_SKEW_MILLIS = 5_000L;

    static Optional<AiDecision> answer(AiTurnContext context) {
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (!prompt.isHidden()) continue;
            Optional<AiDecision> answer = tradeAgreement(context, prompt)
                    .or(() -> researchAgreement(context, prompt))
                    .or(() -> militarySupport(context, prompt))
                    .or(() -> giftOfPrescience(context, prompt));
            if (answer.isPresent()) return answer;
        }
        return Optional.empty();
    }

    static Optional<AiDecision> placeMilitarySupport(AiTurnContext context) {
        Optional<Long> since = context.memory()
                .get(MILITARY_SUPPORT_KEY)
                .filter(StringUtils::isNumeric)
                .map(Long::parseLong);
        if (since.isEmpty()) return Optional.empty();
        Game game = context.game();
        Player seat = context.seat();
        if (NoteHand.holdsForeign(seat, MILITARY_SUPPORT)) {
            context.memory().remove(MILITARY_SUPPORT_KEY);
            return Optional.empty();
        }
        Optional<Match> best = targets(context, since.get() - CLOCK_SKEW_MILLIS).stream()
                .max(Comparator.comparingDouble(
                                (Match match) -> ActionCardRules.frontlineValue(game, seat, planetOf(match)))
                        .thenComparing(match -> match.button().handlerId(), Comparator.reverseOrder()));
        if (best.isPresent()) {
            context.memory().remove(MILITARY_SUPPORT_KEY);
            return Optional.of(best.get().press("place Military Support's 2 infantry on " + planetOf(best.get())));
        }
        if (context.now() > since.get() + PLACEMENT_WAIT_MILLIS)
            context.memory().remove(MILITARY_SUPPORT_KEY);
        return Optional.empty();
    }

    private static Optional<AiDecision> tradeAgreement(AiTurnContext context, AiPrompt prompt) {
        Optional<PromptButton> use = offered(context, prompt, id -> id.startsWith(TRADE_AGREEMENT_OFFER));
        if (use.isEmpty() || !fresh(context, prompt)) return Optional.empty();
        String color = StringUtils.removeStart(use.get().handlerId(), TRADE_AGREEMENT_OFFER);
        Player owner = context.game().getPlayerFromColorOrFaction(color);
        if (owner == null || !NoteHand.holdsForeign(context.seat(), color + TRADE_AGREEMENT)) return Optional.empty();
        if (owner.getCommodities() > 0) {
            return Optional.of(AiDecision.press(
                    prompt, use.get(), "take " + owner.getFaction() + "'s commodities with Trade Agreement"));
        }
        return decline(context, prompt, "keep Trade Agreement: its owner has no commodities");
    }

    private static Optional<AiDecision> researchAgreement(AiTurnContext context, AiPrompt prompt) {
        Optional<PromptButton> acquire = offered(context, prompt, id -> id.startsWith(RESEARCH_AGREEMENT_OFFER));
        if (acquire.isEmpty()
                || !fresh(context, prompt)
                || !NoteHand.holdsForeign(context.seat(), RESEARCH_AGREEMENT)) {
            return Optional.empty();
        }
        String tech = StringUtils.removeStart(acquire.get().handlerId(), RESEARCH_AGREEMENT_OFFER);
        if (ResearchPolicy.value(context.game(), context.seat(), tech) >= RESEARCH_AGREEMENT_MIN_VALUE) {
            return Optional.of(AiDecision.press(prompt, acquire.get(), "gain " + tech + " with Research Agreement"));
        }
        return decline(context, prompt, "keep Research Agreement for a better technology");
    }

    private static Optional<AiDecision> militarySupport(AiTurnContext context, AiPrompt prompt) {
        Optional<PromptButton> play = offered(context, prompt, MILITARY_SUPPORT_OFFER::equals);
        Game game = context.game();
        Player owner = game.getPNOwner(MILITARY_SUPPORT);
        if (play.isEmpty()
                || owner == null
                || prompt.enabledHandler(DECLINE).isEmpty()
                || !NoteHand.holdsForeign(context.seat(), MILITARY_SUPPORT)) {
            return Optional.empty();
        }
        if (game.getActivePlayer() != owner
                || prompt.createdAtMillis() < Prompts.turnStart(context)
                || !game.getStoredValue(ACTION_SUMMARY + owner.getFaction()).isBlank()) {
            return Optional.empty();
        }
        context.memory().put(MILITARY_SUPPORT_KEY, String.valueOf(context.now()));
        return Optional.of(AiDecision.press(
                prompt, play.get(), "play Military Support at the start of " + owner.getFaction() + "'s turn"));
    }

    private static Optional<AiDecision> giftOfPrescience(AiTurnContext context, AiPrompt prompt) {
        Optional<PromptButton> preset = offered(context, prompt, GIFT_PRESET::equals);
        Game game = context.game();
        if (preset.isEmpty()
                || !STRATEGY_PHASE.equalsIgnoreCase(game.getPhaseOfGame())
                || !NoteHand.holdsForeign(context.seat(), GIFT_OF_PRESCIENCE)
                || game.getStoredValue(GIFT_PRESET_STORED).contains(context.faction())) {
            return Optional.empty();
        }
        return Optional.of(
                AiDecision.press(prompt, preset.get(), "pre-play Gift of Prescience to act first this round"));
    }

    private static List<Match> targets(AiTurnContext context, long since) {
        List<Match> targets = new ArrayList<>();
        for (AiPrompt prompt : Prompts.newestFirst(context.prompts())) {
            if (prompt.isHidden() || prompt.createdAtMillis() < since) continue;
            for (PromptButton button : prompt.enabledButtons()) {
                if (button.isOwnedBy(context.faction())
                        && button.handlerId().startsWith(MILITARY_SUPPORT_TARGET)
                        && !context.alreadyPressed(prompt, button)) {
                    targets.add(new Match(prompt, button));
                }
            }
        }
        return targets;
    }

    private static String planetOf(Match match) {
        return StringUtils.removeStart(match.button().handlerId(), MILITARY_SUPPORT_TARGET);
    }

    private static Optional<PromptButton> offered(AiTurnContext context, AiPrompt prompt, Predicate<String> handler) {
        return prompt.firstEnabled(button ->
                button.isUnowned() && handler.test(button.handlerId()) && !context.alreadyPressed(prompt, button));
    }

    private static Optional<AiDecision> decline(AiTurnContext context, AiPrompt prompt, String reason) {
        return offered(context, prompt, DECLINE::equals).map(button -> AiDecision.press(prompt, button, reason));
    }

    private static boolean fresh(AiTurnContext context, AiPrompt prompt) {
        return prompt.createdAtMillis() >= context.now() - OFFER_WINDOW_MILLIS;
    }
}
