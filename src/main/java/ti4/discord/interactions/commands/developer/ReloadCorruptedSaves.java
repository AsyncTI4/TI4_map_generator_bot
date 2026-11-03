package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class ReloadCorruptedSaves extends Subcommand {

    ReloadCorruptedSaves() {
        super("reload_corrupted_saves", "Reloads every game whose save file is corrupt from its latest undo.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Reloading all corrupted saves. This will take a while.");

        List<String> corruptGameNames = corruptGameNames(GameManager.getGameNames());
        var reloadedGames = new ArrayList<String>();
        var failedReloadedGames = new ArrayList<String>();
        for (String gameName : corruptGameNames) {
            if (tryReload(gameName)) reloadedGames.add(gameName);
            else failedReloadedGames.add(gameName);
        }

        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Finished reloading games."
                        + "\nFound " + StringHelper.pluralize(corruptGameNames.size(), "corrupted save")
                        + "\nReloaded: " + reloadedGames
                        + "\nFailed to reload: " + failedReloadedGames);
    }

    static List<String> corruptGameNames(Collection<String> gameNames) {
        return gameNames.stream().filter(GameManager::isCorrupt).sorted().toList();
    }

    private static boolean tryReload(String name) {
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
