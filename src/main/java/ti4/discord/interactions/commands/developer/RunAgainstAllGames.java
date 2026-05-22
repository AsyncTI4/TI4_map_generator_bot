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
    private static final String BAD_MIGRATION_REASON =
            "Developer ran custom command against this game, probably migration related.";

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
            List<GameStats.ActionCardPlay> playsFromUndo = findActionCardPlaysFromUndos(game);
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
    private static List<GameStats.ActionCardPlay> findActionCardPlaysFromUndos(Game game) {
        String gameName = game.getName();
        List<Integer> undoNumbers = GameUndoNameService.getSortedUndoNumbers(gameName);
        if (undoNumbers.isEmpty()) {
            return List.of();
        }

        Integer targetUndoNumber = null;
        if (BAD_MIGRATION_REASON.equals(game.getLatestCommand())) {
            targetUndoNumber = undoNumbers.getLast();
        } else {
            for (int i = undoNumbers.size() - 1; i >= 0; i--) {
                UndoFileInfo undoFileInfo = readUndoFileInfo(gameName, undoNumbers.get(i));
                if (undoFileInfo != null && BAD_MIGRATION_REASON.equals(undoFileInfo.latestCommand())) {
                    if (i <= 0) {
                        return List.of();
                    }
                    targetUndoNumber = undoNumbers.get(i - 1);
                    break;
                }
            }
        }

        if (targetUndoNumber == null) {
            return List.of();
        }

        UndoFileInfo targetUndoFileInfo = readUndoFileInfo(gameName, targetUndoNumber);
        if (targetUndoFileInfo == null || targetUndoFileInfo.actionCardPlays().isEmpty()) {
            return List.of();
        }
        return targetUndoFileInfo.actionCardPlays();
    }

    private static UndoFileInfo readUndoFileInfo(String gameName, int undoNumber) {
        String undoFileName = gameName + "_" + undoNumber + Constants.TXT;
        Path undoFilePath = Storage.getGameUndo(gameName, undoFileName);
        String latestCommandPrefix = Constants.LATEST_COMMAND + " ";
        String gameStatsPrefix = Constants.GAME_STATS + " ";
        String latestCommand = null;
        List<GameStats.ActionCardPlay> actionCardPlays = List.of();
        try {
            for (String line : Files.readAllLines(undoFilePath, Charset.defaultCharset())) {
                if (line.startsWith(latestCommandPrefix)) {
                    latestCommand = line.substring(latestCommandPrefix.length());
                    continue;
                }
                if (line.startsWith(gameStatsPrefix)) {
                    String json = line.substring(gameStatsPrefix.length());
                    actionCardPlays = JsonMapperManager.basic()
                            .readValue(json, GameStats.class)
                            .getActionCardPlays();
                }
            }
        } catch (Exception e) {
            BotLogger.error("Error reading undo file " + undoFileName + " for game " + gameName, e);
            return null;
        }
        return new UndoFileInfo(latestCommand, actionCardPlays);
    }

    private record UndoFileInfo(String latestCommand, List<GameStats.ActionCardPlay> actionCardPlays) {}
}
