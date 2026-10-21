package ti4.ai.eval;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Helper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class IconStake {

    public static final String SEIZE_AN_ICON = "sai";
    private static final double TRAIT_CLAIM_PULL = 2.0;
    private static final int CLAIM_REACH = 2;
    private static final List<String> TRAIT_PUBLICS = List.of("corner", "unify_colonies");
    private static final List<String> TRAITS = List.of("cultural", "hazardous", "industrial");

    public static boolean seeks(Game game, Player seat) {
        return seat.getSecretsUnscored().containsKey(SEIZE_AN_ICON)
                && ListPlayerInfoService.getPlayerProgressOnObjective(SEIZE_AN_ICON, game, seat) == 0;
    }

    public static boolean behindCustodians(Game game, Player seat) {
        return seeks(game, seat)
                && game.getTileMap().values().stream()
                        .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                        .anyMatch(planet -> planet.isLegendary() && BoardView.hasCustodians(planet));
    }

    public static boolean marks(Game game, Player seat, Tile tile) {
        return seeks(game, seat) && holdsLegendaryPlanetOf(game, seat, tile, false);
    }

    public static double freeValue(Game game, Player seat, Tile tile) {
        boolean prize = seeks(game, seat) && holdsLegendaryPlanetOf(game, seat, tile, true);
        return prize ? ObjectiveValue.VICTORY_POINT_VALUE : 0;
    }

    public static List<Tile> freeTiles(Game game, Player seat) {
        if (!seeks(game, seat)) return List.of();
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.isMecatol(game))
                .filter(tile -> !BoardView.hasEnemyUnits(game, seat, tile))
                .filter(tile -> holdsLegendaryPlanetOf(game, seat, tile, true))
                .toList();
    }

    public static Comparator<Planet> claimOrder(Game game, Player seat) {
        boolean seeking = seeks(game, seat);
        List<TraitNeeds.Need> needs = TraitNeeds.of(game, seat);
        Optional<String> publicTrait = leadingPublicTrait(game, seat);
        return Comparator.comparingInt((Planet planet) -> seeking && planet.isLegendary() ? 0 : 1)
                .thenComparing(Comparator.comparingDouble((Planet planet) -> claimValue(planet, needs, publicTrait))
                        .reversed());
    }

    private static double claimValue(Planet planet, List<TraitNeeds.Need> needs, Optional<String> publicTrait) {
        boolean wanted = TraitNeeds.wanted(needs, planet)
                || publicTrait.filter(planet.getPlanetTypes()::contains).isPresent();
        return BoardView.planetValue(planet) + (wanted ? TRAIT_CLAIM_PULL : 0);
    }

    static Optional<String> leadingPublicTrait(Game game, Player seat) {
        boolean chasing = Helper.canPlayerScorePOs(game, seat)
                && TRAIT_PUBLICS.stream()
                        .anyMatch(id -> game.getRevealedPublicObjectives().containsKey(id)
                                && !ObjectivePolicy.hasScored(game, seat, id));
        if (!chasing) return Optional.empty();
        Map<String, Integer> free = freeTraitPlanetsInReach(game, seat);
        return TRAITS.stream()
                .max(Comparator.comparingInt((String trait) -> ownedOfTrait(game, seat, trait))
                        .thenComparingInt(trait -> free.getOrDefault(trait, 0))
                        .thenComparing(Comparator.comparingInt(TRAITS::indexOf).reversed()));
    }

    private static int ownedOfTrait(Game game, Player seat, String trait) {
        return (int) seat.getPlanets().stream()
                .map(name -> game.getPlanetsInfo().get(name))
                .filter(planet -> planet != null && ObjectiveValue.countsForScoring(game, planet))
                .filter(planet -> planet.getPlanetTypes().contains(trait))
                .count();
    }

    private static Map<String, Integer> freeTraitPlanetsInReach(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        Map<String, Integer> free = new HashMap<>();
        if (home == null) return free;
        for (String position :
                MovementGraph.reach(game, seat, home.getPosition(), CLAIM_REACH).keySet()) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) continue;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (BoardView.controller(game, planet.getName()) != null
                        || BoardView.hasCustodians(planet)
                        || !ObjectiveValue.countsForScoring(game, planet)) {
                    continue;
                }
                for (String trait : TRAITS) {
                    if (planet.getPlanetTypes().contains(trait)) free.merge(trait, 1, Integer::sum);
                }
            }
        }
        return free;
    }

    private static boolean holdsLegendaryPlanetOf(Game game, Player seat, Tile tile, boolean freeOnly) {
        return tile.getPlanetUnitHolders().stream()
                .filter(Planet::isLegendary)
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .anyMatch(planet -> !freeOnly || BoardView.controller(game, planet.getName()) == null);
    }
}
