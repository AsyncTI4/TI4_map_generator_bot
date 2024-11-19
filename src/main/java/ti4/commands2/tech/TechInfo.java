package ti4.commands2.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.info.TechInfoService;

class TechInfo extends GameStateSubcommand {

    public TechInfo() {
        super(Constants.INFO, "Send tech information to your Cards Info channel", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TechInfoService.sendTechInfo(getGame(), getPlayer(), event);
    }
}
