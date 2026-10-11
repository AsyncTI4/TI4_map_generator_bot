package ti4.discord.interactions.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.info.LeaderInfoService;

class LeaderInfo extends GameStateSubcommand {

    public LeaderInfo() {
        super(Constants.INFO, "Send Leader info to your Cards-Info thread", false, true);
        addOptions(CommonOptions.factionColorForStats());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        LeaderInfoService.sendLeadersInfo(getGame(), getPlayer(), event);
    }
}
