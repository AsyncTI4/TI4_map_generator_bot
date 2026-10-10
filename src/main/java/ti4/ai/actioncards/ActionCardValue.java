package ti4.ai.actioncards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.SpendUnlock;
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
    private static final double PLAY_THRESHOLD = 2.0;
    private static final double NEAR_LIMIT_THRESHOLD = 1.0;
    private static final double UNLOCKS_OBJECTIVE = 10.0;
    private static final double INFANTRY_VALUE = 0.5;
    private static final double MESSIAH_BASE = 0.5;
    private static final double FRONTLINE_VALUE = 2.0;
    private static final double WAR_EFFORT_VALUE = 2.0;
    private static final int MIN_MESSIAH_PLANETS = 3;
    private static final String TRIAD = "triad";
    private static final double NEVER_USED = 0.0;
    private static final double UNKNOWN = 0.5;
    private static final double LATER_STAGE = 1.0;
    private static final double SABOTAGE = 1.5;
    private static final double ECONOMY_CAP = 3.0;
    private static final double SPARE_BELOW = 2.0;
    private static final Set<String> USELESS = Set.of(
            "intercept",
            "rout",
            "waylay",
            "scramble",
            "reflective",
            "bunker",
            "disable",
            "lost_star",
            "nav_suite",
            "silence_space",
            "solar_flare",
            "master_plan",
            "ghost_squad",
            "bribery",
            "deadly_plot",
            "confounding",
            "confusing",
            "fire_team",
            "courageous",
            "blackmarketdealing",
            "exchangeprogram",
            "lieinwait",
            "crisis",
            "puppetsonastring",
            "coup",
            "disgrace");
    private static final Map<String, Double> STAGE_ONE_KEEP = Map.of(
            MINING, 3.0,
            INDUSTRIAL, 3.0,
            SUMMIT, 3.0,
            ECONOMIC, 2.0,
            MESSIAH, 2.0,
            FRONTLINE, 2.0,
            WAR_EFFORT, 2.0);

    record Candidate(String alias, double value) {}

    public static Optional<Candidate> bestComponentPlay(Game game, Player seat) {
        boolean nearLimit = seat.getAcCount() >= ButtonHelper.getACLimit(game, seat) - 1;
        double threshold = nearLimit ? NEAR_LIMIT_THRESHOLD : PLAY_THRESHOLD;
        return COMPONENT_CARDS.stream()
                .filter(seat.getActionCards()::containsKey)
                .map(alias -> new Candidate(alias, playValue(game, seat, alias)))
                .filter(candidate -> candidate.value() >= threshold)
                .max(Comparator.comparingDouble(Candidate::value));
    }

    static double playValue(Game game, Player seat, String alias) {
        return switch (alias) {
            case MINING -> {
                int gain = miningGain(game, seat);
                yield gain + (SpendUnlock.unlocks(game, seat, List.of(), gain) ? UNLOCKS_OBJECTIVE : 0);
            }
            case INDUSTRIAL -> {
                int gain = ButtonHelper.getNumberOfXTypePlanets(seat, game, "industrial", true);
                yield gain + (SpendUnlock.unlocks(game, seat, List.of(), gain) ? UNLOCKS_OBJECTIVE : 0);
            }
            case ECONOMIC ->
                SpendUnlock.unlocks(game, seat, exhaustedCulturalPlanets(game, seat), 0) ? UNLOCKS_OBJECTIVE : 0;
            case MESSIAH -> {
                int planets = messiahPlanets(game, seat);
                yield planets >= MIN_MESSIAH_PLANETS ? MESSIAH_BASE + INFANTRY_VALUE * planets : 0;
            }
            case FRONTLINE -> seat.getPlanets().isEmpty() ? 0 : FRONTLINE_VALUE;
            case WAR_EFFORT -> canPlaceCruiser(game, seat) ? WAR_EFFORT_VALUE : 0;
            default -> 0;
        };
    }

    public static double keepValue(Game game, Player seat, String alias) {
        if (alias.startsWith("sabo")) return SABOTAGE;
        if (USELESS.contains(alias) || alias.startsWith("dh")) return NEVER_USED;
        Double stageOne = STAGE_ONE_KEEP.get(alias);
        if (stageOne != null) return Math.max(stageOne, Math.min(playValue(game, seat, alias), ECONOMY_CAP));
        if (alias.startsWith("mb") || alias.startsWith("sh") || alias.contains("rider") || alias.startsWith("veto")) {
            return LATER_STAGE;
        }
        return UNKNOWN;
    }

    public static List<String> discardOrder(Game game, Player seat) {
        List<String> aliases = new ArrayList<>(seat.getActionCards().keySet());
        aliases.sort(Comparator.comparingDouble((String alias) -> keepValue(game, seat, alias))
                .thenComparingDouble(alias -> playValue(game, seat, alias))
                .thenComparing(Comparator.naturalOrder()));
        return aliases;
    }

    public static boolean hasSpareCard(Game game, Player seat) {
        List<String> order = discardOrder(game, seat);
        if (order.isEmpty()) return false;
        String worst = order.getFirst();
        boolean wouldPlay = bestComponentPlay(game, seat)
                .map(Candidate::alias)
                .filter(worst::equals)
                .isPresent();
        return keepValue(game, seat, worst) < SPARE_BELOW && !wouldPlay;
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
            if (BoardView.nonFighterShips(space, seat) + 1 <= seat.getEffectiveFleetCC()) tiles.add(tile);
        }
        return tiles;
    }
}
