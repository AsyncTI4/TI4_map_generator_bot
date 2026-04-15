package ti4.discord.interactions.commands.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.Mapper;

public class PlanetRemove extends PlanetAddRemove {

    public PlanetRemove() {
        super(Constants.PLANET_REMOVE, "Remove Planet");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        player.removePlanet(planet);
        Planet unitHolder = game.getPlanetsInfo().get(planet);
        removePlayerControlToken(player, unitHolder);

        if (game.mecatols().contains(planet) && player.hasCustodiaVigilia() && unitHolder != null) {
            unitHolder.setSpaceCannonDieCount(0);
            unitHolder.setSpaceCannonHitsOn(0);
        }
    }

    public static void removePlayerControlToken(Player player, UnitHolder unitHolder) {
        String color = player.getColor();
        if (unitHolder == null || color == null || "null".equals(color)) {
            return;
        }
        String ccID = Mapper.getControlID(color);
        String ccPath = Mapper.getCCPath(ccID);
        if (ccPath != null) {
            unitHolder.removeControl(ccID);
        }
    }
}
