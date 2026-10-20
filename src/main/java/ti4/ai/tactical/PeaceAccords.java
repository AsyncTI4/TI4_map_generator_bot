package ti4.ai.tactical;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Territory;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperAbilities;

@UtilityClass
public class PeaceAccords {

    public static final String BUTTON_PREFIX = "peaceAccords_";
    private static final String ABILITY = "peace_accords";
    private static final double RIVAL_PLANET_DENIAL_SHARE = 0.5;

    public record Claim(String planet, double value) {}

    private record Context(Game game, Player seat, ObjectiveValue objectives, Territory territory) {}

    public static boolean applies(Game game, Player seat) {
        return seat.hasAbility(ABILITY) && !game.isTwilightsFallMode();
    }

    public static String planetOf(String handlerId) {
        return StringUtils.removeStart(handlerId, BUTTON_PREFIX);
    }

    public static List<Claim> claims(Game game, Player seat) {
        if (!applies(game, seat)) return List.of();
        List<String> claimable = claimablePlanets(game, seat);
        if (claimable.isEmpty()) return List.of();
        Context context = new Context(game, seat, new ObjectiveValue(game, seat), Territory.of(game, seat));
        return claimable.stream()
                .map(planet -> new Claim(planet, value(context, planet)))
                .sorted(Comparator.comparingDouble(Claim::value).reversed().thenComparing(Claim::planet))
                .toList();
    }

    public static Optional<Claim> best(Game game, Player seat) {
        return claims(game, seat).stream().findFirst();
    }

    public static double bestValue(Game game, Player seat) {
        return best(game, seat).map(Claim::value).orElse(0.0);
    }

    private static double value(Context context, String planetName) {
        Game game = context.game();
        Planet planet = game.getPlanetsInfo().get(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null) return 0;
        double worth = BoardView.planetValue(planet);
        Player holder = BoardView.controller(game, planetName);
        double denial = holder == null || holder == context.seat() ? 0 : RIVAL_PLANET_DENIAL_SHARE * worth;
        ObjectiveValue objectives = context.objectives();
        double objective = Math.max(0, objectives.gain(objectives.before().withPlanet(planetName)));
        return worth + denial + objective + TacticalPlanner.claimedPlanetBonus(game, context.territory(), tile);
    }

    private static List<String> claimablePlanets(Game game, Player seat) {
        return ButtonHelperAbilities.getXxchaPeaceAccordsButtons(game, seat, null, "").stream()
                .map(button -> button.getCustomId())
                .filter(id -> id != null && id.startsWith(BUTTON_PREFIX))
                .map(PeaceAccords::planetOf)
                .distinct()
                .toList();
    }
}
