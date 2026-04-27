package ti4.discord.interactions.commands.developer;

import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class ReloadAllCorruptedSaves extends Subcommand {

    ReloadAllCorruptedSaves() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> reloadedGames = new HashSet<>();
        Set<String> failedReloadedGames = new HashSet<>();
        var managedGames = GameManager.getManagedGames();
        for (ManagedGame managedGame : managedGames) {
            try {
                managedGame.getGame();
            } catch (Exception e) {
                if (tryReload(managedGame.getName())) reloadedGames.add(managedGame.getName());
                else failedReloadedGames.add(managedGame.getName());
            }
        }

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Finished reloading games."
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
