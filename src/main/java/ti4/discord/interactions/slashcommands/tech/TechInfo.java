package ti4.discord.interactions.slashcommands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.info.TechInfoService;

class TechInfo extends GameStateSubcommand {

    TechInfo() {
        super(Constants.INFO, "Send technology information to your #cards-info thread", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TechInfoService.sendTechInfo(getPlayer(), event);
    }
}
