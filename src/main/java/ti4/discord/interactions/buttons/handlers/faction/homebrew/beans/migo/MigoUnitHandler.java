package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.model.Mapper;

@UtilityClass
public class MigoUnitHandler {
    private static final String NEVERMEN = "migo_mech";
    private static final String NEVERMEN_ASYNC_ID = "mf";
    private static final String NEVERMEN_SPECIALTY = "attachment_migomechspec.png";

    public static boolean hasNevermenSpecialty(Player player, Planet planet) {
        return player.hasUnit(NEVERMEN)
                && planet.getUnitCount(Mapper.getUnitKey(NEVERMEN_ASYNC_ID, player.getColor())) >= 2;
    }

    public static void syncNevermen(Game game) {
        for (Planet planet : game.getPlanetsInfo().values()) {
            Player controller = game.getPlayerThatControlsPlanet(planet.getName(), true);
            if (controller != null && hasNevermenSpecialty(controller, planet)) {
                planet.addToken(NEVERMEN_SPECIALTY);
            } else {
                planet.removeToken(NEVERMEN_SPECIALTY);
            }
        }
    }
}
