package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Thrones;

import java.util.Set;

import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class ThronesAbilityHandler {
    private static final Set<String> THRONE_PLANETS = Set.of("cineron", "gyraxis", "lethara", "skarnath");

    public static boolean isThronePlanet(String planetName) {
        return THRONE_PLANETS.contains(planetName);
    }

    public static boolean tracesOfRuinIsActive(Game game) {
        return game.getRealPlayers().stream().anyMatch(player -> player.hasAbility("traces_of_ruin"));
    }
}
