package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.nekro.CommandTokenPolicy;
import ti4.ai.tactical.Stall;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;

@UtilityClass
public class AiActionCards {

    static final String SABOTAGE = "sabotage";
    private static final ToDoubleBiFunction<Game, Player> ALWAYS = (game, seat) -> 1.0;
    private static final Evaluator NEVER = (game, seat) -> Optional.empty();
    private static final double SUMMIT_TOKEN_VALUE = 2.0;

    // TODO Exchange Program is not played: its agreement needs a negotiation and the other way costs both players a
    // fleet token.
    private static final List<AiCard> CARDS = List.of(
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
                    ALWAYS,
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
            new AiCard(
                    SABOTAGE,
                    ActionCardHelper::isSabotageOrShatter,
                    Window.REACTION,
                    NEVER,
                    ALWAYS,
                    null,
                    List.of(),
                    null));

    public static boolean usable(String alias) {
        return of(alias).isPresent();
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
                ALWAYS,
                resolveHandler,
                targetPrefixes,
                target);
    }

    static AiCard reaction(
            String key,
            Window window,
            Evaluator evaluator,
            String resolveHandler,
            String targetPrefix,
            TargetStep target) {
        return card(key, window, evaluator, resolveHandler, List.of(targetPrefix), target);
    }

    private static AiCard card(
            String key,
            Window window,
            Evaluator evaluator,
            String resolveHandler,
            List<String> targetPrefixes,
            TargetStep target) {
        return new AiCard(key, key::equals, window, evaluator, ALWAYS, resolveHandler, targetPrefixes, target);
    }

    private static AiCard economy(String alias, String resolveHandler, TargetStep target) {
        Predicate<String> matches = alias::equals;
        return new AiCard(
                alias,
                matches,
                Window.ECONOMY,
                (game, seat) -> ActionCardValue.economyPlan(game, seat, alias),
                ALWAYS,
                resolveHandler,
                List.of(),
                target);
    }

    static Evaluator stalling(Evaluator evaluator) {
        return withStall(evaluator);
    }

    private static Evaluator withStall(Evaluator evaluator) {
        return (game, seat) -> evaluator.plan(game, seat).map(plan -> plan.plus(Stall.actionValue(game, seat)));
    }

    private static double summitValue(Game game, Player seat) {
        return SUMMIT_TOKEN_VALUE
                * Math.min(EconomyTargets.SUMMIT_TOKENS, CommandTokenPolicy.gainWithinReinforcements(game, seat));
    }
}
