package ti4.commands2.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.tech.PlayerTechService;

class TechRefresh extends TechAddRemove {

    public TechRefresh() {
        super(Constants.TECH_REFRESH, "Ready a technology");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.refreshTech(event, player, techID);
    }
}
