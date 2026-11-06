package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.fow.setup.FowSetupWizardService;

class SetupWizardBeta extends GameStateSubcommand {

    SetupWizardBeta() {
        super(Constants.SETUP_BETA, "Open (or refresh) the new single-panel FoW GM setup wizard", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.getPlayersWithGMRole().contains(getPlayer())) {
            MessageHelper.replyToMessage(event, "You are not GM in this game.");
            return;
        }
        FowSetupWizardService.openContainerPanel(game);
        MessageHelper.replyToMessage(event, "Setup panel posted in the GM room.");
    }
}
