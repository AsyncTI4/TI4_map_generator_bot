package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.service.tech.PlayerTechService;

class TechUnpurge extends TechAddRemove {

    public TechUnpurge() {
        super(Constants.TECH_UNPURGE, "Un-purge a technology that was previously purged");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.unpurgeTech(event, player, techID);
    }
}
