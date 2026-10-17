package ti4.ai.actioncards;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.actioncards.CardPrompts.Offer;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
class PlayerTargetCards {

    static final String SPY = "spy";
    static final String INSUBORDINATION = "insub";
    static final String FORWARD_SUPPLY_BASE = "fsb";
    private static final double TACTIC_TOKEN_VALUE = 0.75;
    static final int LOST_BUILD_MIN_RESOURCES = 3;
    static final int LOST_BUILD_CAP = 6;
    static final double LOST_BUILD_PER_RESOURCE = 0.3;
    private static final double CONTENDER_WEIGHT = 100_000.0;
    private static final double FORWARD_SUPPLY_BASE_TRADE_GOODS = 3.0;
    private static final String SPY_PREFIX = "spyStep2_";
    private static final String INSUBORDINATION_PREFIX = "insubStep2_";
    private static final String FORWARD_SUPPLY_BASE_PREFIX = "forwardSupplyBaseStep2_";

    static AiCard spy() {
        return AiActionCards.component(
                SPY,
                PlayerTargetCards::spyPlan,
                "resolveSpyStep1",
                SPY_PREFIX,
                chooseFaction(SPY_PREFIX, PlayerTargetCards::spyWeight, "Spy"));
    }

    static AiCard insubordination() {
        return AiActionCards.component(
                INSUBORDINATION,
                PlayerTargetCards::insubordinationPlan,
                "resolveInsubStep1",
                INSUBORDINATION_PREFIX,
                chooseFaction(INSUBORDINATION_PREFIX, PlayerTargetCards::insubordinationWeight, "Insubordination"));
    }

    static AiCard forwardSupplyBase() {
        return AiActionCards.reaction(
                FORWARD_SUPPLY_BASE,
                Window.AFTER_ACTIVATION,
                (game, seat, activated) -> FoWHelper.playerHasUnitsInSystem(seat, activated),
                PlayerTargetCards::forwardSupplyBasePlan,
                "forwardSupplyBase",
                FORWARD_SUPPLY_BASE_PREFIX,
                chooseFaction(
                        FORWARD_SUPPLY_BASE_PREFIX, PlayerTargetCards::forwardSupplyBaseWeight, "Forward Supply Base"));
    }

    static Optional<Plan> spyPlan(Game game, Player seat) {
        return others(game, seat).stream()
                .filter(other -> other.getAcCount() > 0)
                .max(Comparator.comparing((Player other) -> ScoringStakes.contender(game, seat, other))
                        .thenComparingInt(Player::getAcCount)
                        .thenComparingInt(Player::getTotalVictoryPoints)
                        .thenComparing(Player::getFaction, Comparator.reverseOrder()))
                .map(target -> Plan.aimedAt(
                        ActionCardPrior.AVERAGE_CARD * TableStanding.of(game, seat, target),
                        target.getFaction(),
                        "on " + target.getRepresentationNoPing() + "."));
    }

    static Optional<Plan> insubordinationPlan(Game game, Player seat) {
        return others(game, seat).stream()
                .filter(other -> other.getTacticalCC() > 0 && !other.isPassed())
                .max(Comparator.comparing((Player other) -> ScoringStakes.contender(game, seat, other))
                        .thenComparingDouble(other -> insubordinationValue(game, seat, other))
                        .thenComparingInt(Player::getTotalVictoryPoints)
                        .thenComparingInt(Player::getTacticalCC)
                        .thenComparing(Player::getFaction, Comparator.reverseOrder()))
                .map(target -> Plan.aimedAt(
                        insubordinationValue(game, seat, target),
                        target.getFaction(),
                        "against " + target.getRepresentationNoPing() + "'s tactic pool."));
    }

    static double insubordinationValue(Game game, Player seat, Player target) {
        double token = TACTIC_TOKEN_VALUE + lostBuild(game, target);
        return token * TableStanding.of(game, seat, target);
    }

    private static double lostBuild(Game game, Player target) {
        if (target.getTacticalCC() != 1 || target.isPassed() || !hasProduction(game, target)) return 0;
        Wallet wallet = Wallet.of(game, target);
        int unspent = wallet.resources() + wallet.tradeGoods();
        return unspent >= LOST_BUILD_MIN_RESOURCES ? Math.min(unspent, LOST_BUILD_CAP) * LOST_BUILD_PER_RESOURCE : 0;
    }

    private static boolean hasProduction(Game game, Player target) {
        return !ButtonHelper.getTilesOfPlayersSpecificUnits(game, target, UnitType.Spacedock)
                .isEmpty();
    }

    static Optional<Plan> forwardSupplyBasePlan(Game game, Player seat) {
        Player active = game.getActivePlayer();
        List<Player> candidates = others(game, seat);
        List<Player> avoidingTheActivePlayer =
                candidates.stream().filter(other -> other != active).toList();
        List<Player> pool = avoidingTheActivePlayer.isEmpty() ? candidates : avoidingTheActivePlayer;
        return pool.stream()
                .min(Comparator.comparingInt(Player::getTotalVictoryPoints).thenComparing(Player::getFaction))
                .map(target -> Plan.aimedAt(
                        FORWARD_SUPPLY_BASE_TRADE_GOODS,
                        target.getFaction(),
                        "and gives the other trade good to " + target.getRepresentationNoPing() + "."));
    }

    private static List<Player> others(Game game, Player seat) {
        return game.getRealPlayers().stream().filter(other -> other != seat).toList();
    }

    private static ToDoubleFunction<Player> spyWeight(Game game, Player seat) {
        return other -> contenderWeight(game, seat, other) + other.getAcCount() * 100.0 + other.getTotalVictoryPoints();
    }

    private static ToDoubleFunction<Player> insubordinationWeight(Game game, Player seat) {
        return other -> contenderWeight(game, seat, other)
                + insubordinationValue(game, seat, other) * 10_000.0
                + other.getTotalVictoryPoints() * 100.0
                + other.getTacticalCC();
    }

    private static ToDoubleFunction<Player> forwardSupplyBaseWeight(Game game, Player seat) {
        Player active = game.getActivePlayer();
        return other -> (other == active ? -1000.0 : 0.0) - other.getTotalVictoryPoints();
    }

    private static double contenderWeight(Game game, Player seat, Player other) {
        return ScoringStakes.contender(game, seat, other) ? CONTENDER_WEIGHT : 0;
    }

    private interface Weight {
        ToDoubleFunction<Player> of(Game game, Player seat);
    }

    private static TargetStep chooseFaction(String prefix, Weight weight, String card) {
        return (context, key, play) -> {
            Game game = context.game();
            ToDoubleFunction<Player> weigh = weight.of(game, context.seat());
            List<Offer> offers = CardPrompts.targetOffers(context, play, prefix);
            Optional<Offer> choice = CardPrompts.choose(offers, play.extra(), faction -> {
                Player target = game.getPlayerFromColorOrFaction(faction);
                return target == null ? Double.NEGATIVE_INFINITY : weigh.applyAsDouble(target);
            });
            return choice.map(offer -> finish(context, key, play, offer, card));
        };
    }

    private static AiDecision finish(AiTurnContext context, String key, CardPlay play, Offer offer, String card) {
        PlayMemory.moveTo(context, key, play, Stage.DONE);
        return offer.match().press("choose " + offer.suffix() + " for " + card);
    }
}
