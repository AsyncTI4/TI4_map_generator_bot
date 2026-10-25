package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.actioncards.CardPrompts.Offer;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.ResearchPolicy;
import ti4.ai.strategy.StrategyCardRules;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperActionCards;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;

@UtilityClass
class ResearchCards {

    static final String FOCUSED_RESEARCH = "f_researched";
    static final String DIVERT_FUNDING = "divert_funding";
    static final String PLAGIARIZE = "plagiarize";
    private static final String GET_TECHNOLOGY = "acquireATech";
    private static final String RETURN_PREFIX = "divertFunding@";
    private static final String COPY_PREFIX = "getTech_";
    private static final String NO_PAYMENT = "__";
    private static final String EXHAUST_DONE = "deleteButtons_spitItOut";
    private static final int FOCUSED_RESEARCH_TRADE_GOODS = 4;
    private static final int PLAGIARIZE_INFLUENCE = 5;
    private static final double TRADE_GOOD = 1.0;

    static AiCard focusedResearch() {
        return AiActionCards.component(
                FOCUSED_RESEARCH,
                ResearchCards::focusedResearchPlan,
                "focusedResearch",
                GET_TECHNOLOGY,
                ResearchCards::getTechnology);
    }

    static AiCard divertFunding() {
        return AiActionCards.component(
                DIVERT_FUNDING,
                ResearchCards::divertFundingPlan,
                "getDivertFundingButtons",
                List.of(RETURN_PREFIX, GET_TECHNOLOGY),
                ResearchCards::divertFundingStep);
    }

    static AiCard plagiarize() {
        return AiActionCards.component(
                PLAGIARIZE, ResearchCards::plagiarizePlan, "getPlagiarizeButtons", COPY_PREFIX, ResearchCards::copy);
    }

    static Optional<Plan> focusedResearchPlan(Game game, Player seat) {
        if (StrategyCardRules.cannotResearch(seat)) return Optional.empty();
        if (ScoringReserve.planAfterReserve(game, seat, SpendCost.tradeGoods(FOCUSED_RESEARCH_TRADE_GOODS))
                .isEmpty()) {
            return Optional.empty();
        }
        return ResearchPolicy.bestResearchable(game, seat)
                .map(tech -> Plan.of(researchGain(game, seat, tech, 0) - FOCUSED_RESEARCH_TRADE_GOODS * TRADE_GOOD));
    }

    private static double researchGain(Game game, Player seat, String tech, double valueGivenUp) {
        int points = ResearchPolicy.publicPointsCompletedBy(game, seat, tech);
        double tempo = ResearchPolicy.TECH_TEMPO * (ResearchPolicy.researchValue(game, seat, tech) - valueGivenUp);
        return tempo + ScoringStakes.scoreValue(game, seat, points);
    }

    private static Optional<AiDecision> getTechnology(AiTurnContext context, String key, CardPlay play) {
        Optional<Offer> get =
                CardPrompts.targetOffers(context, play, GET_TECHNOLOGY).stream().findFirst();
        get.ifPresent(offer -> {
            StrategyCardRules.expectResearch(context, 0);
            PlayMemory.moveTo(context, key, play, Stage.DONE);
        });
        return get.map(offer -> offer.match().press("research the technology Focused Research paid for"));
    }

    static Optional<Plan> divertFundingPlan(Game game, Player seat) {
        if (StrategyCardRules.cannotResearch(seat)) return Optional.empty();
        Optional<String> returned = returnable(seat).stream().min(heldValueOrder());
        Optional<String> gained = ResearchPolicy.bestResearchable(game, seat);
        if (returned.isEmpty() || gained.isEmpty()) return Optional.empty();
        double value = researchGain(game, seat, gained.get(), ResearchPolicy.heldValue(returned.get()));
        return Optional.of(Plan.aimedAt(value, returned.get(), "returning " + nameOf(returned.get()) + "."));
    }

    private static List<String> returnable(Player seat) {
        return seat.getTechs().stream()
                .filter(alias -> {
                    TechnologyModel tech = Mapper.getTech(alias);
                    return tech != null
                            && !tech.isUnitUpgrade()
                            && tech.getFaction().filter(StringUtils::isNotBlank).isEmpty();
                })
                .toList();
    }

    private static Comparator<String> heldValueOrder() {
        return Comparator.comparingDouble(ResearchPolicy::heldValue).thenComparing(Comparator.naturalOrder());
    }

    private static String nameOf(String alias) {
        TechnologyModel tech = Mapper.getTech(alias);
        return tech == null ? alias : tech.getName();
    }

    private static Optional<AiDecision> divertFundingStep(AiTurnContext context, String key, CardPlay play) {
        List<Offer> returns =
                CardPrompts.ownedOffers(context, play.playedAt() - CardPrompts.CLOCK_SKEW_MILLIS, RETURN_PREFIX);
        if (!returns.isEmpty()) {
            Optional<Offer> choice =
                    CardPrompts.choose(returns, play.extra(), alias -> -ResearchPolicy.heldValue(alias));
            choice.ifPresent(offer -> StrategyCardRules.expectResearchOtherThan(context, 0, offer.suffix()));
            return choice.map(offer -> offer.match().press("return " + offer.suffix() + " for Divert Funding"));
        }
        Optional<Offer> get =
                CardPrompts.targetOffers(context, play, GET_TECHNOLOGY).stream().findFirst();
        get.ifPresent(offer -> PlayMemory.moveTo(context, key, play, Stage.DONE));
        return get.map(offer -> offer.match().press("research the technology Divert Funding paid for"));
    }

    static Optional<Plan> plagiarizePlan(Game game, Player seat) {
        Optional<Wallet.Payment> payment = payment(game, seat);
        if (payment.isEmpty()) return Optional.empty();
        double cost = InfluencePayments.cost(game, seat, payment.get());
        return copyable(game, seat).stream()
                .map(alias -> Plan.aimedAt(
                        ResearchPolicy.TECH_TEMPO * ResearchPolicy.value(game, seat, alias)
                                + ScoringStakes.scoreValue(
                                        game, seat, ResearchPolicy.publicPointsCompletedBy(game, seat, alias))
                                - cost,
                        alias,
                        "to copy " + nameOf(alias) + "."))
                .max(Comparator.comparingDouble(Plan::value).thenComparing(Plan::target, Comparator.reverseOrder()));
    }

    private static List<String> copyable(Game game, Player seat) {
        return ButtonHelperActionCards.getPlagiarizeButtons(game, seat).stream()
                .map(button -> aliasOf(button.getCustomId()))
                .toList();
    }

    private static String aliasOf(String customId) {
        return StringUtils.substringBefore(StringUtils.removeStart(customId, COPY_PREFIX), NO_PAYMENT);
    }

    private static Optional<Wallet.Payment> payment(Game game, Player seat) {
        return InfluencePayments.payment(game, seat, PLAGIARIZE_INFLUENCE);
    }

    private static Optional<AiDecision> copy(AiTurnContext context, String key, CardPlay play) {
        Game game = context.game();
        Player seat = context.seat();
        List<Offer> offers = CardPrompts.unmentionedTargetOffers(context, play, COPY_PREFIX);
        List<Offer> byAlias = offers.stream()
                .map(offer -> new Offer(offer.match(), aliasOf(COPY_PREFIX + offer.suffix())))
                .toList();
        Optional<Offer> choice =
                CardPrompts.choose(byAlias, play.extra(), alias -> ResearchPolicy.value(game, seat, alias));
        if (choice.isEmpty()) return Optional.empty();
        payment(game, seat)
                .ifPresent(payment -> ti4.ai.scoring.PaymentRules.expect(context, "Plagiarize", payment, EXHAUST_DONE));
        PlayMemory.moveTo(context, key, play, Stage.DONE);
        return Optional.of(choice.get().match().press("copy " + choice.get().suffix() + " with Plagiarize"));
    }
}
