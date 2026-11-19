package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.service.megagame.MegagameTradeOverviewService;

class MegagameTradeOverview extends MegagameGmSubcommand {

    MegagameTradeOverview() {
        super("trade_overview", "Create the trade overview channel, or fix who can see it", false);
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        if (!MegagameTradeOverviewService.isActive(game)) {
            return "The trade overview needs the `megagame` tag and is not available in Fog of War.";
        }
        return MegagameTradeOverviewService.ensureChannel(game);
    }
}
