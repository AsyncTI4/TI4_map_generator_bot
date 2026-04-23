package ti4.discord.interactions.slashcommands.franken;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.franken.FrankenFactionTechService;

class FactionTechRemove extends FactionTechAddRemove {

    FactionTechRemove() {
        super(Constants.FACTION_TECH_REMOVE, "Remove a faction technology from your faction");
    }

    @Override
    public void doAction(Player player, List<String> techIDs, SlashCommandInteractionEvent event) {
        FrankenFactionTechService.removeFactionTechs(event, player, techIDs);
    }
}
