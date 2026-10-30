package ti4.ai.legendary;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.model.Mapper;
import ti4.model.PlanetModel;

@UtilityClass
public class LegendaryPlanets {

    public static final String MALLICE = "mallice";
    public static final String HOPES_END = "hopesend";
    public static final String PRIMOR = "primor";
    public static final String MIRAGE = "mirage";
    public static final String ILLUSION = "illusion";
    public static final String PHANTASM = "phantasm";
    public static final String MECATOL_REX = "mrte";
    public static final String THUNDERS_EDGE = "thundersedge";
    public static final String EMELPAR = "emelpar";
    public static final String ORDINIAN = "ordinian";
    public static final String ORDINIAN_C4 = "ordinianc4";
    public static final String FAUNUS = "faunus";
    public static final String INDUSTREX = "industrex";
    public static final String TEMPESTA = "tempesta";
    public static final String AVERNUS = "avernus";
    public static final String CUSTODIA_VIGILIA = "custodiavigilia";
    public static final String ABILITY_PREFIX = "planetAbilityExhaust_";

    public static final List<String> AT_END_OF_TURN =
            List.of(THUNDERS_EDGE, EMELPAR, MALLICE, HOPES_END, PRIMOR, MIRAGE, ILLUSION, PHANTASM);
    public static final List<String> WHEN_PASSING = List.of(ORDINIAN, ORDINIAN_C4, INDUSTREX, FAUNUS);
    public static final Set<String> FLIGHT_ACADEMIES = Set.of(MIRAGE, ILLUSION, PHANTASM);
    public static final Set<String> ORDINIANS = Set.of(ORDINIAN, ORDINIAN_C4);

    private static final Map<String, Double> HOLDING_VALUES = Map.ofEntries(
            Map.entry(MALLICE, 4.5),
            Map.entry(HOPES_END, 4.5),
            Map.entry(PRIMOR, 3.5),
            Map.entry(MIRAGE, 2.5),
            Map.entry(ILLUSION, 2.5),
            Map.entry(PHANTASM, 2.5),
            Map.entry(MECATOL_REX, 3.5),
            Map.entry(THUNDERS_EDGE, 6.0),
            Map.entry(EMELPAR, 4.5),
            Map.entry(ORDINIAN, 5.5),
            Map.entry(ORDINIAN_C4, 5.5),
            Map.entry(FAUNUS, 4.0),
            Map.entry(INDUSTREX, 4.5),
            Map.entry(TEMPESTA, 3.0),
            Map.entry(CUSTODIA_VIGILIA, 4.0));

    public static boolean isOfficial(String planet) {
        PlanetModel model = Mapper.getPlanet(planet);
        return model != null
                && model.isLegendary()
                && model.getSource() != null
                && model.getSource().isOfficial();
    }

    public static boolean isLegendary(Game game, String name) {
        Planet planet = game.getPlanetsInfo().get(name);
        if (planet != null) return planet.isLegendary();
        PlanetModel model = Mapper.getPlanet(name);
        return model != null && model.isLegendary();
    }

    public static double holdingValue(@Nullable String planet) {
        if (planet == null || !isOfficial(planet)) return BoardView.LEGENDARY_VALUE;
        return HOLDING_VALUES.getOrDefault(planet, BoardView.LEGENDARY_VALUE);
    }

    public static boolean abilityReady(Player seat, String planet) {
        return seat.hasPlanet(planet) && !seat.getExhaustedPlanetsAbilities().contains(planet);
    }

    public static String button(String planet) {
        return ABILITY_PREFIX + planet;
    }
}
