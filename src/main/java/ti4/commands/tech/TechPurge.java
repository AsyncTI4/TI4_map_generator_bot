package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.tech.PlayerTechService;

class TechPurge extends TechAddRemove {

    public TechPurge() {
        super(Constants.TECH_PURGE, "Purge a technology (permanently removing it from the game)");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.purgeTech(event, player, techID);
    }
}
