package ti4.discord.interactions.commands.developer;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.GameUndoNameService;

class RunAgainstAllGames extends Subcommand {

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    boolean changed = makeChanges(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(
                                game, "Developer ran custom command against this game, probably migration related.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static boolean makeChanges(Game game) {
        boolean changed = false;

        // TODO: Remove after actionCardPlays migration is confirmed complete.
        // If actionCardPlays was wiped by a bad migration, restore from the most recent undo that has data.
        if (game.getGameStats().getActionCardPlays().isEmpty()) {
            List<GameStats.ActionCardPlay> playsFromUndo = findActionCardPlaysFromUndos(game.getName());
            if (!playsFromUndo.isEmpty()) {
                game.getGameStats().setActionCardPlays(playsFromUndo);
                changed = true;
            }
        }

        boolean removedInvalid = game.getGameStats()
                .getActionCardPlays()
                .removeIf(play -> !Mapper.isValidActionCard(play.getActionCard()));
        return changed || removedInvalid;
    }

    // TODO: Remove after actionCardPlays migration is confirmed complete.
    private static List<GameStats.ActionCardPlay> findActionCardPlaysFromUndos(String gameName) {
        List<Integer> undoNumbers = GameUndoNameService.getSortedUndoNumbers(gameName);
        String gameStatsPrefix = Constants.GAME_STATS + " ";
        for (int i = undoNumbers.size() - 1; i >= 0; i--) {
            String undoFileName = gameName + "_" + undoNumbers.get(i) + Constants.TXT;
            Path undoFilePath = Storage.getGameUndo(gameName, undoFileName);
            try {
                for (String line : Files.readAllLines(undoFilePath, Charset.defaultCharset())) {
                    if (line.startsWith(gameStatsPrefix)) {
                        String json = line.substring(gameStatsPrefix.length());
                        GameStats stats = JsonMapperManager.basic().readValue(json, GameStats.class);
                        if (!stats.getActionCardPlays().isEmpty()) {
                            return stats.getActionCardPlays();
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                BotLogger.error("Error reading undo file " + undoFileName + " for game " + gameName, e);
            }
        }
        return List.of();
    }
}
