package ti4.commands.planet;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlanetExhaust extends PlanetAddRemove {

    public PlanetExhaust() {
        super(Constants.PLANET_EXHAUST, "Exhaust Planet");
    }

    @Override
    public void doAction(GenericInteractionCreateEvent event, Player player, String planet, Game game) {
        doAction(player, planet, game);
    }

    public static void doAction(Player player, String planet, Game game) {
        doAction(player, planet, game, true);
    }

    public static void doAction(Player player, String planet, Game game, boolean triggerOlradin) {
        if (!player.getPlanets().contains(planet)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " the bot doesn't think you have planet by the name of " + planet);
        }
        if (!player.hasPlanetReady(planet))
            return;
        if (triggerOlradin) {
            DiscordantStarsHelper.handleOlradinPoliciesWhenExhaustingPlanets(game, player, planet);
        }
        player.exhaustPlanet(planet);
    }

}
