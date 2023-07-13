package ti4.commands.planet;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.PlayerSubcommandData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

public class PlanetRefreshAll extends PlanetSubcommandData {
    public PlanetRefreshAll() {
        super(Constants.PLANET_REFRESH_ALL, "Ready All Planets");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        player = Helper.getPlayer(activeMap, player, event);

        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        for (String planet : player.getPlanets()) {
            player.refreshPlanet(planet);
        }
        sendMessage(Helper.getPlayerRepresentation(player, activeMap) + " readied all planets.");
    }
}
