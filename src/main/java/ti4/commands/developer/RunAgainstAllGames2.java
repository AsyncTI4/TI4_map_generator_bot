package ti4.commands.developer;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;

class RunAgainstAllGames2 extends Subcommand {

    RunAgainstAllGames2() {
        super("run_against_all_games2", "Lists games with mismatched player counts.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        List<String> mismatchedGames = new ArrayList<>();
        List<String> homeSystemMismatches = new ArrayList<>();
        GamesPage.consumeAllGames(game -> {
            if (game.isHasEnded() && game.getWinner().isEmpty()) {
                return;
            }
            int playerCountForMap = game.getPlayerCountForMap();
            int realPlayerCount = game.getRealAndEliminatedPlayers().size();
            if (playerCountForMap != realPlayerCount) {
                mismatchedGames.add(game.getName() + " (player count: " + playerCountForMap + ", real player count: "
                        + realPlayerCount + ")");
            }
            if (!game.isMinorFactionsMode()) {
                long homeSystemCount = game.getTileMap().values().stream()
                        .filter(tile -> tile.isHomeSystem())
                        .count();
                if (playerCountForMap != homeSystemCount) {
                    StringBuilder entry = new StringBuilder(game.getName())
                            .append(" (player count: ")
                            .append(playerCountForMap)
                            .append(", real player count: ")
                            .append(realPlayerCount)
                            .append(", home systems: ")
                            .append(homeSystemCount)
                            .append(") HS: PC difference");
                    if (realPlayerCount == homeSystemCount) {
                        entry.append(", RPC match");
                    }
                    homeSystemMismatches.add(entry.toString());
                }
            }
        });

        if (mismatchedGames.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No games with mismatched player counts found.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Games with mismatched player counts:\n" + String.join("\n", mismatchedGames));
        }
        if (homeSystemMismatches.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "No games with mismatched home system counts found.");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Games with player count vs home system differences:\n" + String.join("\n", homeSystemMismatches));
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Found " + mismatchedGames.size() + " games with mismatched player counts out of "
                + GameManager.getGameCount() + " games: " + String.join(", ", mismatchedGames));
        BotLogger.info("Found " + homeSystemMismatches.size()
                + " games with player count vs home system differences out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", homeSystemMismatches));
    }
}
