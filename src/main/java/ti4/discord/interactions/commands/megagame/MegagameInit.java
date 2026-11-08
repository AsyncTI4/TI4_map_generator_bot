package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupService;

class MegagameInit extends MegagameGmSubcommand {

    MegagameInit() {
        super("init", "Build groups from held strategy cards, or from seat order if nobody holds one", true);
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        String warning = MegagameGroupService.initGroups(game);
        return withSummary(
                game,
                "Groups built. Strategy cards per player set to " + game.getStrategyCardsPerPlayer() + ". "
                        + "Check the speakers, then `/megagame enable`. " + warning);
    }
}
