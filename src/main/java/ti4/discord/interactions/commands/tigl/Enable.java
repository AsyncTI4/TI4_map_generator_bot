package ti4.discord.interactions.commands.tigl;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.tigl.TiglSetupService;

class Enable extends GameStateSubcommand {

    Enable() {
        super(Constants.TIGL_ENABLE, "Set whether this game is a TIGL game, and on which ladder", true, false);
        addOptions(
                new OptionData(OptionType.STRING, Constants.TIGL_MODE, "Leave empty to post the choice buttons instead")
                        .addChoice("Standard Ladder", TiglSetupService.STANDARD)
                        .addChoice("Fractured Ladder", TiglSetupService.FRACTURED)
                        .addChoice("False - not a TIGL game", TiglSetupService.CASUAL));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!TiglSetupService.mayChangeLadder(game, event)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), TiglSetupService.ladderLockedMessage());
            return;
        }

        String mode = event.getOption(Constants.TIGL_MODE, null, OptionMapping::getAsString);
        if (mode == null) {
            TiglSetupService.postLadderPrompt(game, event.getChannel());
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                TiglSetupService.applyLadderChoice(game, mode).message());
    }
}
