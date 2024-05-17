package ti4.commands.planet;

import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlanetRefresh extends PlanetAddRemove {
    public PlanetRefresh() {
        super(Constants.PLANET_REFRESH, "Ready Planet");
    }

    @Override
    public void doAction(Player player, String planet, Game activeGame) {
        if(!player.getPlanets().contains(planet)){
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()+" the bot doesnt think you have a planet by the name of "+planet);
        }
        player.refreshPlanet(planet);
    }
}
