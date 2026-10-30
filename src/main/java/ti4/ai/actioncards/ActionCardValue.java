package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.tactical.Stall;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;

@UtilityClass
public class ActionCardValue {

    static final String MINING = "mining_initiative";
    static final String INDUSTRIAL = "industrial_initiative";
    static final String ECONOMIC = "economic_initiative";
    static final String MESSIAH = "messiah";
    static final String FRONTLINE = "f_deployment";
    static final String WAR_EFFORT = "war_effort";
    static final String SUMMIT = "summit";
    static final List<String> COMPONENT_CARDS = List.of(MINING, INDUSTRIAL, ECONOMIC, MESSIAH, FRONTLINE, WAR_EFFORT);
    static final double ECONOMY_CAP = 3.0;
    private static final double PLAY_THRESHOLD = 2.0;
    private static final double NEAR_LIMIT_THRESHOLD = 1.0;
    private static final double INFANTRY_VALUE = 0.5;
    private static final double MESSIAH_BASE = 0.5;
    private static final double FRONTLINE_VALUE = 2.0;
    private static final double WAR_EFFORT_VALUE = 2.0;
    private static final int MIN_MESSIAH_PLANETS = 3;
    private static final String TRIAD = "triad";
    private static final double NOT_USABLE = 0.0;
    static final double EXTRA_SABOTAGE_SHARE = 0.6;
    private static final double SPARE_BELOW = 2.0;

    record Candidate(String alias, double value, boolean urgent) {

        Candidate(String alias, double value) {
            this(alias, value, false);
        }

        double margin() {
            return value - ActionCardPrior.keepPrior(alias);
        }
    }

    public static Optional<Candidate> bestComponentPlay(Game game, Player seat) {
        return bestComponentPlay(game, seat, PlayMoment.IN_TURN);
    }

    static Optional<Candidate> bestComponentPlay(Game game, Player seat, PlayMoment moment) {
        if (!AiSettings.usesLegacyActionCards(seat)) return bestNewComponentPlay(game, seat, moment);
        boolean nearLimit = seat.getAcCount() >= ButtonHelper.getACLimit(game, seat) - 1;
        double threshold = nearLimit ? NEAR_LIMIT_THRESHOLD : PLAY_THRESHOLD;
        return COMPONENT_CARDS.stream()
                .filter(seat.getActionCards()::containsKey)
                .map(alias -> new Candidate(alias, playValue(game, seat, alias)))
                .filter(candidate -> candidate.value() >= threshold)
                .max(Comparator.comparingDouble(Candidate::value));
    }

    static Optional<Candidate> bestNewComponentPlay(Game game, Player seat) {
        return bestNewComponentPlay(game, seat, PlayMoment.IN_TURN);
    }

    static Optional<Candidate> bestNewComponentPlay(Game game, Player seat, PlayMoment moment) {
        return bestPlay(game, seat, moment, Window.ECONOMY, Window.COMPONENT_ACTION);
    }

    static Optional<Candidate> bestReactionPlay(Game game, Player seat, Window window) {
        return bestPlay(game, seat, PlayMoment.IN_TURN, window);
    }

    private static Optional<Candidate> bestPlay(Game game, Player seat, PlayMoment moment, Window... windows) {
        if (AiSettings.usesLegacyActionCards(seat)) return Optional.empty();
        return seat.getActionCards().keySet().stream()
                .map(alias -> candidate(game, seat, alias, List.of(windows), moment))
                .flatMap(Optional::stream)
                .filter(candidate -> candidate.value()
                        >= HandPressure.share(game, seat, moment, candidate.alias())
                                * ActionCardPrior.keepPrior(candidate.alias()))
                .max(Comparator.comparingDouble(Candidate::margin)
                        .thenComparingDouble(Candidate::value)
                        .thenComparing(Candidate::alias, Comparator.reverseOrder()));
    }

    private static Optional<Candidate> candidate(
            Game game, Player seat, String alias, List<Window> windows, PlayMoment moment) {
        return AiActionCards.of(alias)
                .filter(card -> windows.contains(card.window()))
                .flatMap(card -> card.planFor(game, seat, moment))
                .map(plan -> new Candidate(alias, plan.value(), plan.urgent()));
    }

    static double playValue(Game game, Player seat, String alias) {
        return switch (alias) {
            case MINING -> {
                int gain = miningGain(game, seat);
                yield gain + ScoringStakes.scoreValue(game, seat, SpendUnlock.pointDelta(game, seat, gain));
            }
            case INDUSTRIAL -> {
                int gain = ButtonHelper.getNumberOfXTypePlanets(seat, game, "industrial", true);
                yield gain + ScoringStakes.scoreValue(game, seat, SpendUnlock.pointDelta(game, seat, gain));
            }
            case ECONOMIC ->
                ScoringStakes.scoreValue(
                        game, seat, SpendUnlock.pointsUnlocked(game, seat, exhaustedCulturalPlanets(game, seat), 0));
            case MESSIAH -> {
                int planets = messiahPlanets(game, seat);
                yield planets >= MIN_MESSIAH_PLANETS ? MESSIAH_BASE + INFANTRY_VALUE * planets : 0;
            }
            case FRONTLINE -> seat.getPlanets().isEmpty() ? 0 : FRONTLINE_VALUE;
            case WAR_EFFORT -> canPlaceCruiser(game, seat) ? WAR_EFFORT_VALUE : 0;
            default -> 0;
        };
    }

    static Optional<Plan> economyPlan(Game game, Player seat, String alias) {
        if (!isPlayable(game, seat, alias)) return Optional.empty();
        double value = playValue(game, seat, alias);
        if (ECONOMIC.equals(alias)) {
            value += ReadyingValue.of(game, seat, exhaustedCulturalPlanets(game, seat));
        }
        return Optional.of(Plan.of(value + Stall.cardActionValue(game, seat)));
    }

    private static boolean isPlayable(Game game, Player seat, String alias) {
        return switch (alias) {
            case MINING, FRONTLINE -> !seat.getPlanets().isEmpty();
            case MESSIAH -> messiahPlanets(game, seat) > 0;
            case WAR_EFFORT -> canPlaceCruiser(game, seat);
            default -> true;
        };
    }

    public static double keepValue(Game game, Player seat, String alias) {
        if (AiSettings.usesLegacyActionCards(seat)) return LegacyKeepValue.keepValue(game, seat, alias);
        Optional<AiCard> card = AiActionCards.of(alias);
        if (card.isEmpty()) return NOT_USABLE;
        double prior = ActionCardPrior.keepPrior(alias);
        if (card.get().isSabotage()) return sabotageKeep(game, seat, alias, prior);
        double held = card.get().keepFloorFor(game, seat, alias);
        double now = Math.min(card.get().playValueFor(game, seat), ECONOMY_CAP);
        return Math.max(held, now);
    }

    private static double sabotageKeep(Game game, Player seat, String alias, double prior) {
        long earlierCopies = seat.getActionCards().keySet().stream()
                .filter(AiActionCards::isSabotage)
                .filter(other -> other.compareTo(alias) < 0)
                .count();
        if (earlierCopies > 0) return prior * EXTRA_SABOTAGE_SHARE;
        return Math.max(prior, SabotagePolicy.reserve(game, seat, ""));
    }

    private static final class DiscardKey {
        private final String alias;
        private final double keep;
        private final double prior;
        private final DoubleSupplier playValue;
        private Double play;

        private DiscardKey(String alias, double keep, double prior, DoubleSupplier playValue) {
            this.alias = alias;
            this.keep = keep;
            this.prior = prior;
            this.playValue = playValue;
        }

        private String alias() {
            return alias;
        }

        private double keep() {
            return keep;
        }

        private double prior() {
            return prior;
        }

        private double play() {
            if (play == null) play = playValue.getAsDouble();
            return play;
        }
    }

    public static List<String> discardOrder(Game game, Player seat) {
        return discardKeys(game, seat).stream().map(DiscardKey::alias).toList();
    }

    private static List<DiscardKey> discardKeys(Game game, Player seat) {
        try (Thinking.Span ignored = Thinking.enter("cards.discardOrder")) {
            return seat.getActionCards().keySet().stream()
                    .map(alias -> new DiscardKey(
                            alias,
                            timedKeep(game, seat, alias),
                            ActionCardPrior.keepPrior(alias),
                            () -> timedPlay(game, seat, alias)))
                    .sorted(Comparator.comparingDouble(DiscardKey::keep)
                            .thenComparingDouble(DiscardKey::prior)
                            .thenComparingDouble(DiscardKey::play)
                            .thenComparing(DiscardKey::alias))
                    .toList();
        }
    }

    private static double timedKeep(Game game, Player seat, String alias) {
        try (Thinking.Span ignored = Thinking.enter("cards.keepValue", () -> alias)) {
            return keepValue(game, seat, alias);
        }
    }

    private static double timedPlay(Game game, Player seat, String alias) {
        try (Thinking.Span ignored = Thinking.enter("cards.playValue")) {
            return playValue(game, seat, alias);
        }
    }

    public static Optional<String> spareCard(Game game, Player seat) {
        List<DiscardKey> keys = discardKeys(game, seat);
        if (keys.isEmpty()) return Optional.empty();
        DiscardKey worst = keys.getFirst();
        if (worst.keep() >= SPARE_BELOW || wouldPlayComponent(game, seat, worst.alias())) return Optional.empty();
        return Optional.of(worst.alias());
    }

    public static boolean hasSpareCard(Game game, Player seat) {
        return spareCard(game, seat).isPresent();
    }

    private static boolean wouldPlayComponent(Game game, Player seat, String alias) {
        try (Thinking.Span ignored = Thinking.enter("cards.bestComponentPlay")) {
            return bestComponentPlay(game, seat)
                    .map(Candidate::alias)
                    .filter(alias::equals)
                    .isPresent();
        }
    }

    public static Optional<PromptButton> worstDiscard(
            Game game, Player seat, AiPrompt prompt, String prefix, String suffix, Predicate<PromptButton> usable) {
        for (String alias : discardOrder(game, seat)) {
            Integer index = seat.getActionCards().get(alias);
            if (index == null) continue;
            String id = prefix + index + suffix;
            Optional<PromptButton> button =
                    prompt.firstEnabled(candidate -> id.equals(candidate.handlerId()) && usable.test(candidate));
            if (button.isPresent()) return button;
        }
        return Optional.empty();
    }

    static int miningGain(Game game, Player seat) {
        int best = 0;
        for (String name : seat.getPlanets()) {
            if (TRIAD.equals(name)) continue;
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet != null) best = Math.max(best, planet.getResources());
        }
        return best;
    }

    static List<String> exhaustedCulturalPlanets(Game game, Player seat) {
        return seat.getExhaustedPlanets().stream()
                .filter(planet -> ButtonHelper.getTypeOfPlanet(game, planet).contains("cultural"))
                .toList();
    }

    static int messiahPlanets(Game game, Player seat) {
        int count = 0;
        for (String name : seat.getPlanets()) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet == null || planet.isSpaceStation(game)) continue;
            boolean blocked = planet.getTokenList().stream()
                    .anyMatch(token -> token.contains("dmz")
                            || token.contains(Constants.WORLD_DESTROYED)
                            || token.contains("arcane_shield"));
            if (!blocked) count++;
        }
        return count;
    }

    static boolean canPlaceCruiser(Game game, Player seat) {
        int cap = seat.getUnitCap("ca");
        if (cap > 0 && ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, "ca") >= cap) return false;
        return cruiserTiles(game, seat).stream().findAny().isPresent();
    }

    static List<Tile> cruiserTiles(Game game, Player seat) {
        List<Tile> tiles = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = BoardView.space(tile);
            if (!BoardView.hasOwnShips(seat, tile)) continue;
            if (BoardView.nonFighterShips(space, seat) + 1 <= FleetLimit.ships(seat)) tiles.add(tile);
        }
        return tiles;
    }
}
