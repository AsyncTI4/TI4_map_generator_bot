package ti4.commands.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenFactionTechService;

class FactionTechRemove extends FactionTechAddRemove {

    public FactionTechRemove() {
        super(Constants.FACTION_TECH_REMOVE, "Remove a faction technology from your faction");
    }
    
    @Override
    public void doAction(Player player, List<String> techIDs, SlashCommandInteractionEvent event) {
        FrankenFactionTechService.removeFactionTechs(event, player, techIDs);
    }
}
