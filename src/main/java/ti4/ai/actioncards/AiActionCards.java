package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiCard.KeepFloor;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.agenda.RiderPayoff;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.MoveBoost;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.tactical.Stall;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;

@UtilityClass
public class AiActionCards {

    static final String SABOTAGE = "sabotage";
    private static final KeepFloor PRIOR = (game, seat, alias) -> ActionCardPrior.keepPrior(alias);
    private static final Evaluator NEVER = (game, seat) -> Optional.empty();
    private static final double SUMMIT_TOKEN_VALUE = 2.0;

    // TODO Exchange Program is not played: its agreement needs a negotiation and the other way costs both players a
    // fleet token.
    private static final List<AiCard> CARDS = Stream.concat(
                    Stream.of(
                            economy(ActionCardValue.MINING, "miningInitiative", null),
                            economy(ActionCardValue.INDUSTRIAL, "industrialInitiative", null),
                            economy(ActionCardValue.ECONOMIC, "economicInitiative", null),
                            economy(ActionCardValue.MESSIAH, "riseOfAMessiah", null),
                            economy(ActionCardValue.FRONTLINE, "resolveFrontline", EconomyTargets::frontline),
                            economy(ActionCardValue.WAR_EFFORT, "resolveWarEffort", EconomyTargets::warEffort),
                            new AiCard(
                                    ActionCardValue.SUMMIT,
                                    ActionCardValue.SUMMIT::equals,
                                    Window.PRE_PLAY,
                                    (game, seat) -> Optional.of(Plan.of(summitValue(game, seat))),
                                    PRIOR,
                                    "resolveSummit",
                                    List.of(),
                                    EconomyTargets::summitTokens),
                            PlayerTargetCards.spy(),
                            PlayerTargetCards.insubordination(),
                            PlayerTargetCards.forwardSupplyBase(),
                            PlanetTargetCards.uprising(),
                            PlanetTargetCards.archaeologicalExpedition(),
                            SystemTargetCards.ghostShip(),
                            SystemTargetCards.explorationProbe(),
                            BoardCards.fighterConscription(),
                            BoardCards.refitTroops(),
                            ResearchCards.focusedResearch(),
                            ResearchCards.divertFunding(),
                            ResearchCards.plagiarize(),
                            TokenCards.unexpectedAction(),
                            TokenCards.signalJamming(),
                            NeutralUnitCards.pirateContract(),
                            NeutralUnitCards.pirateFleet(),
                            NeutralUnitCards.mercenaryContract(),
                            StrikeCards.crippleDefenses(),
                            StrikeCards.plague(),
                            StrikeCards.unstablePlanet(),
                            StrikeCards.reactorMeltdown(),
                            StrikeCards.tacticalBombardment(),
                            StrikeCards.luckyShot(),
                            ShipCards.scuttle(),
                            TableCards.seizeArtifact(),
                            TableCards.repealLaw(),
                            TableCards.brilliance(),
                            SecretCards.impersonation(),
                            StrategizeCard.strategize(),
                            OverruleCard.overrule(),
                            AgendaCards.veto(),
                            AgendaCards.rider(RiderPayoff.IMPERIAL),
                            AgendaCards.rider(RiderPayoff.TRADE),
                            AgendaCards.rider(RiderPayoff.POLITICS),
                            AgendaCards.rider(RiderPayoff.TECHNOLOGY),
                            AgendaCards.rider(RiderPayoff.LEADERSHIP),
                            ExtraActionCard.masterPlan(),
                            ExtraActionCard.counterstroke(),
                            ProductionCards.warMachine(),
                            new AiCard(
                                    MoveBoost.FLANK_SPEED,
                                    MoveBoost::isFlankSpeed,
                                    Window.AFTER_ACTIVATION,
                                    NEVER,
                                    PRIOR,
                                    null,
                                    List.of(),
                                    null),
                            new AiCard(
                                    SABOTAGE,
                                    ActionCardHelper::isSabotageOrShatter,
                                    Window.REACTION,
                                    NEVER,
                                    PRIOR,
                                    null,
                                    List.of(),
                                    null)),
                    Stream.concat(CombatCards.all().stream(), HitCancelCards.all().stream()))
            .toList();

    public static boolean usable(String alias) {
        return of(alias).isPresent();
    }

    public static boolean isComponentCard(String alias) {
        return of(alias).filter(AiCard::isComponentAction).isPresent();
    }

    public static boolean isSabotage(String alias) {
        return of(alias).filter(AiCard::isSabotage).isPresent();
    }

    public static Set<String> targetPrefixes() {
        return CARDS.stream().flatMap(card -> card.targetPrefixes().stream()).collect(Collectors.toUnmodifiableSet());
    }

    static Optional<AiCard> of(String alias) {
        return CARDS.stream().filter(card -> card.matches().test(alias)).findFirst();
    }

    static List<AiCard> inWindow(Window window) {
        return CARDS.stream().filter(card -> card.window() == window).toList();
    }

    static List<AiCard> componentCards() {
        return CARDS.stream().filter(AiCard::isComponentAction).toList();
    }

    static Stream<AiCard> all() {
        return CARDS.stream();
    }

    static AiCard component(
            String key, Evaluator evaluator, String resolveHandler, String targetPrefix, TargetStep target) {
        return component(key, evaluator, resolveHandler, List.of(targetPrefix), target);
    }

    static AiCard component(
            String key, Evaluator evaluator, String resolveHandler, List<String> targetPrefixes, TargetStep target) {
        return card(key, Window.COMPONENT_ACTION, withStall(evaluator), resolveHandler, targetPrefixes, target);
    }

    static AiCard componentWithResolveHook(
            String key,
            Evaluator evaluator,
            String resolveHandler,
            List<String> targetPrefixes,
            TargetStep target,
            Consumer<AiTurnContext> onResolve) {
        return new AiCard(
                key,
                key::equals,
                Window.COMPONENT_ACTION,
                withStall(evaluator),
                PRIOR,
                resolveHandler,
                targetPrefixes,
                target,
                onResolve);
    }

    static AiCard combatRound(String key, String resolveHandler) {
        return new AiCard(key, key::equals, Window.COMBAT_ROUND, NEVER, PRIOR, resolveHandler, List.of(), null);
    }

    static AiCard combatRound(
            String key, KeepFloor keepFloor, String resolveHandler, List<String> targetPrefixes, TargetStep target) {
        return new AiCard(
                key, key::equals, Window.COMBAT_ROUND, NEVER, keepFloor, resolveHandler, targetPrefixes, target);
    }

    static AiCard hitAssignment(String key, List<String> targetPrefixes, TargetStep target) {
        return new AiCard(key, key::equals, Window.HIT_ASSIGNMENT, NEVER, PRIOR, null, targetPrefixes, target);
    }

    static AiCard production(String key, Predicate<String> matches) {
        return new AiCard(key, matches, Window.PRODUCTION, NEVER, PRIOR, null, List.of(), null);
    }

    static AiCard afterAction(String key, Predicate<String> matches, TargetStep target) {
        return new AiCard(key, matches, Window.AFTER_ACTION, NEVER, PRIOR, null, List.of(), target);
    }

    static AiCard componentOfCopies(
            String key,
            Predicate<String> matches,
            Evaluator evaluator,
            String resolveHandler,
            List<String> targetPrefixes,
            TargetStep target) {
        return new AiCard(
                key,
                matches,
                Window.COMPONENT_ACTION,
                withStall(evaluator),
                PRIOR,
                resolveHandler,
                targetPrefixes,
                target);
    }

    static AiCard reaction(
            String key,
            Window window,
            ActivationTest condition,
            Evaluator evaluator,
            String resolveHandler,
            String targetPrefix,
            TargetStep target) {
        return card(key, window, whenActivated(condition, evaluator), resolveHandler, List.of(targetPrefix), target);
    }

    static AiCard reactionWithPlayButton(
            String key,
            Predicate<String> matches,
            ActivationTest condition,
            Evaluator evaluator,
            List<String> targetPrefixes,
            TargetStep target,
            Function<Game, String> playSuffix) {
        return new AiCard(
                key,
                matches,
                Window.AFTER_ACTIVATION,
                whenActivated(condition, evaluator),
                PRIOR,
                null,
                targetPrefixes,
                target,
                context -> {},
                playSuffix);
    }

    private static Evaluator whenActivated(ActivationTest condition, Evaluator evaluator) {
        return (game, seat) -> {
            Tile activated = game.getTileByPosition(game.getActiveSystem());
            if (activated == null || !condition.holds(game, seat, activated)) return Optional.empty();
            return evaluator.plan(game, seat);
        };
    }

    private static AiCard card(
            String key,
            Window window,
            Evaluator evaluator,
            String resolveHandler,
            List<String> targetPrefixes,
            TargetStep target) {
        return new AiCard(key, key::equals, window, evaluator, PRIOR, resolveHandler, targetPrefixes, target);
    }

    private static AiCard economy(String alias, String resolveHandler, TargetStep target) {
        Predicate<String> matches = alias::equals;
        return new AiCard(
                alias,
                matches,
                Window.ECONOMY,
                (game, seat) -> ActionCardValue.economyPlan(game, seat, alias),
                PRIOR,
                resolveHandler,
                List.of(),
                target);
    }

    private static Evaluator withStall(Evaluator evaluator) {
        return Evaluator.atMoment((game, seat, moment) ->
                evaluator.plan(game, seat, moment).map(plan -> plan.plus(Stall.cardActionValue(game, seat))));
    }

    private static double summitValue(Game game, Player seat) {
        return SUMMIT_TOKEN_VALUE
                * Math.min(EconomyTargets.SUMMIT_TOKENS, CommandTokenPolicy.gainWithinReinforcements(game, seat));
    }
}
