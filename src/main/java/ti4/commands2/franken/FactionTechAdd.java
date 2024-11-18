package ti4.commands2.franken;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.franken.FrankenFactionTechService;

class FactionTechAdd extends FactionTechAddRemove {

    public FactionTechAdd() {
        super(Constants.FACTION_TECH_ADD, "Add a faction tech to your faction");
    }

    @Override
    public void doAction(Player player, List<String> techIDs, SlashCommandInteractionEvent event) {
        FrankenFactionTechService.addFactionTechs(event, player, techIDs);
    }
}
