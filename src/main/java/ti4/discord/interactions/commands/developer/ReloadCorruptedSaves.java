package ti4.discord.interactions.commands.developer;

import java.util.HashSet;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class ReloadCorruptedSaves extends Subcommand {

    ReloadCorruptedSaves() {
        super("reload_corrupted_saves", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Reloading all corrupted saves. This will take a while.");

        int successCount = 0;
        var reloadedGames = new HashSet<String>();
        var failedReloadedGames = new HashSet<String>();
        var managedGames = GameManager.getManagedGames();
        for (ManagedGame managedGame : managedGames) {
            try {
                managedGame.getGame();
                successCount++;
            } catch (Exception e) {
                if (tryReload(managedGame.getName())) reloadedGames.add(managedGame.getName());
                else failedReloadedGames.add(managedGame.getName());
            }
        }

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Finished reloading games."
                        + "\nSuccessfully loaded: " + successCount + " games"
                        + "\nReloaded games: " + reloadedGames
                        + "\nFailed reloaded games: " + failedReloadedGames);
    }

    private boolean tryReload(String name) {
        Game reloadedGame = null;
        try {
            reloadedGame = GameManager.reload(name);
        } catch (Exception e) {
            BotLogger.error("Error while reloading game " + name + ". Needs someone to fix it manually.", e);
        }
        if (reloadedGame != null) {
            MessageChannel messageChannel = reloadedGame.getMainGameChannel();
            MessageHelper.sendMessageToChannel(
                    messageChannel,
                    "Developer reloaded your game from its latest undo file, probably migration or hot fix related.");
            return true;
        }
        return false;
    }
}
