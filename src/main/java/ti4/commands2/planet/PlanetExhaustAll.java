package ti4.commands2.planet;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlanetExhaustAll extends GameStateSubcommand {

    public PlanetExhaustAll() {
        super(Constants.PLANET_EXHAUST_ALL, "Exhaust All Planets", true, true);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        for (String planet : player.getPlanets()) {
            player.exhaustPlanet(planet);
        }
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + " exhausted all planets.");
    }
}
