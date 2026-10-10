package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.fow.FogGameSummaryService;

class FowInfo extends GameStateSubcommand {

    public FowInfo() {
        super(
                Constants.INFO,
                "Fog game settings, options, players and channels (GM room or admin/developer)",
                false,
                false);
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game Name").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isFowMode() && !game.isLightFogMode()) {
            MessageHelper.replyToMessage(event, "This command only works in fog games. Use `/game info` instead.");
            return;
        }
        if (!canViewFogInfo(game, event)) {
            MessageHelper.replyToMessage(
                    event, "Only a GM of this game in the GM room, an admin or a developer can use this command.");
            return;
        }
        MessageHelper.sendMessageToChannelWithEmbeds(
                event.getChannel(),
                "## Fog Info: " + FogGameSummaryService.displayName(game),
                FogGameSummaryService.buildEmbeds(game, true));
    }

    private static boolean canViewFogInfo(Game game, SlashCommandInteractionEvent event) {
        if (CommandHelper.hasRole(event, JdaService.developerRoles)) {
            return true;
        }
        return FoWHelper.isGameMasterInGmRoom(game, event);
    }
}
