package ti4.commands2.relic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.info.RelicInfoService;

class RelicInfo extends GameStateSubcommand {

    public RelicInfo() {
        super(Constants.RELIC_INFO, "Send relic information to your #cards-info thread", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        RelicInfoService.sendRelicInfo(getGame(), getPlayer(), event);
    }
}
