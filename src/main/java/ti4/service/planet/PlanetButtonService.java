package ti4.service.planet;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.PlanetLocation;
import ti4.game.Player;

/** Creates selection buttons from a visible, caller-supplied owned-planet rule. */
@UtilityClass
public class PlanetButtonService {

    public static List<Button> buttonsForOwnedPlanets(
            Player player,
            Game game,
            Predicate<PlanetLocation> eligible,
            Function<PlanetLocation, Button> buttonFactory) {
        return game.streamControlledPlanetLocations(player)
                .filter(eligible)
                .map(buttonFactory)
                .toList();
    }

    public static List<Button> buttonsForOwnedPlanets(
            Player player, Game game, Predicate<PlanetLocation> eligible, ButtonStyle style, String idPrefix) {
        return buttonsForOwnedPlanets(
                player, game, eligible, location -> planetButton(location, game, style, idPrefix));
    }

    public static List<Button> buttonsForUsablePlanets(
            Player player,
            Game game,
            Predicate<PlanetLocation> eligible,
            Function<PlanetLocation, Button> buttonFactory) {
        return game.streamUsablePlanetLocations(player)
                .filter(eligible)
                .map(buttonFactory)
                .toList();
    }

    public static List<Button> buttonsForControlledPlanets(
            Player player, Game game, Predicate<Planet> eligible, Function<Planet, Button> buttonFactory) {
        return game.streamControlledPlanets(player)
                .filter(eligible)
                .map(buttonFactory)
                .toList();
    }

    public static List<Button> buttonsForControlledPlanets(
            Player player, Game game, Predicate<Planet> eligible, ButtonStyle style, String idPrefix) {
        return buttonsForControlledPlanets(
                player,
                game,
                eligible,
                planet -> planetButton(planet.getName(), planet.getRepresentation(game), style, idPrefix));
    }

    public static List<Button> buttonsForUsablePlanets(
            Player player, Game game, Predicate<PlanetLocation> eligible, ButtonStyle style, String idPrefix) {
        return buttonsForUsablePlanets(
                player, game, eligible, location -> planetButton(location, game, style, idPrefix));
    }

    private static Button planetButton(PlanetLocation location, Game game, ButtonStyle style, String idPrefix) {
        return planetButton(location.planet().getName(), location.planet().getRepresentation(game), style, idPrefix);
    }

    private static Button planetButton(String planetName, String label, ButtonStyle style, String idPrefix) {
        String id = idPrefix + planetName;
        return switch (style) {
            case PRIMARY -> Buttons.blue(id, label);
            case SUCCESS -> Buttons.green(id, label);
            case DANGER -> Buttons.red(id, label);
            default -> Buttons.gray(id, label);
        };
    }
}
