package ti4.commands.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlanetRefresh extends PlanetAddRemove {
    public PlanetRefresh() {
        super(Constants.PLANET_REFRESH, "Ready Planet");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        doAction(player, planet);
    }

    public static void doAction(Player player, String planet) {
        if (!player.getPlanets().contains(planet)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " the bot doesn't think you have a planet by the name of " + planet);
        }
        player.refreshPlanet(planet);
    }
}
