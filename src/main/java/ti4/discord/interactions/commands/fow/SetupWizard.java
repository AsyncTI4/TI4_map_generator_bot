package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.message.MessageHelper;
import ti4.service.fow.setup.FowSetupWizardService;

/** GM-only, GM-room-only setup wizard. Re-running this command doubles as "refresh" - it deletes the
 * previous panel and reposts whatever step the wizard is currently on. */
class SetupWizard extends GameStateSubcommand {

    SetupWizard() {
        // saveGame must be true: openOrRefresh mutates wizard state (panel message id, intro-shown flag,
        // etc.) via Game.setStoredValue on every call, not just the first. saveGame=false would both skip
        // persisting that (CommandGameState.postExecute only calls GameManager.save when saveGame=true)
        // and run this under a READ lock instead of WRITE, racing other mutations on the same game.
        super("setup", "Open (or refresh) the FoW GM setup wizard", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.getPlayersWithGMRole().contains(getPlayer())) {
            MessageHelper.replyToMessage(event, "You are not GM in this game.");
            return;
        }
        FowSetupWizardService.openOrRefresh(game);
        MessageHelper.replyToMessage(event, "Setup wizard posted in the GM room.");
    }
}
