package ti4.discord.interactions.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.tech.PlayerTechService;

class TechUnpurge extends TechAddRemove {

    TechUnpurge() {
        super(Constants.TECH_UNPURGE, "Un-purge a technology that was previously purged");
    }

    @Override
    public void doAction(Player player, String techID, SlashCommandInteractionEvent event) {
        PlayerTechService.unpurgeTech(event, player, techID);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
