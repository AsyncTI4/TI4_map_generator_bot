package ti4.discord.interactions.commands.developer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ResourceHelper;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import tools.jackson.databind.json.JsonMapper;

class RunAgainstAllGames extends Subcommand {

    private static final String OLD_SABOTAGE_DATA_FILE = "oldsabotagedata.txt";
    private static final JsonMapper mapper = JsonMapperManager.basic();

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Map<String, GameStats> oldSabotageData = loadOldSabotageData();
        if (oldSabotageData.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "No old sabotage data found in `" + OLD_SABOTAGE_DATA_FILE + "`.");
            return;
        }

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeGames(
                oldSabotageData.keySet(),
                game -> {
                    boolean changed = mergeMissingActionCardPlays(game, oldSabotageData.get(game.getName()));
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(game, "Restored old sabotage action card play statistics.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static Map<String, GameStats> loadOldSabotageData() {
        String filePath = ResourceHelper.getResourceFromFolder("", OLD_SABOTAGE_DATA_FILE);
        if (filePath == null) {
            BotLogger.error("Could not find old sabotage data file: " + OLD_SABOTAGE_DATA_FILE);
            return Map.of();
        }

        try {
            Map<String, GameStats> oldSabotageData = new HashMap<>();
            for (String line : Files.readAllLines(Path.of(filePath), StandardCharsets.UTF_8)) {
                parseOldSabotageDataLine(line).forEach((gameName, gameStats) -> oldSabotageData
                        .computeIfAbsent(gameName, ignored -> new GameStats())
                        .getActionCardPlays()
                        .addAll(gameStats.getActionCardPlays()));
            }
            return oldSabotageData;
        } catch (IOException e) {
            BotLogger.error("Failed to read old sabotage data file: " + OLD_SABOTAGE_DATA_FILE, e);
            return Map.of();
        }
    }

    private static Map<String, GameStats> parseOldSabotageDataLine(String line) {
        if (line == null || line.isBlank()) {
            return Map.of();
        }

        int separatorIndex = line.indexOf(' ');
        if (separatorIndex < 1 || separatorIndex == line.length() - 1) {
            BotLogger.warning("Skipping malformed old sabotage data line: " + line);
            return Map.of();
        }

        String gameName = line.substring(0, separatorIndex);
        String gameStatsJson = line.substring(separatorIndex + 1);
        try {
            return Map.of(gameName, mapper.readValue(gameStatsJson, GameStats.class));
        } catch (Exception e) {
            BotLogger.error("Skipping old sabotage data line for game `" + gameName + "` due to invalid JSON.", e);
            return Map.of();
        }
    }

    private static boolean mergeMissingActionCardPlays(Game game, GameStats oldGameStats) {
        if (oldGameStats == null) return false;

        List<ActionCardPlay> oldPlays = oldGameStats.getActionCardPlays();
        if (oldPlays == null || oldPlays.isEmpty()) return false;

        List<ActionCardPlay> currentPlays = game.getGameStats().getActionCardPlays();

        for (ActionCardPlay play : currentPlays) {
            oldPlays.remove(play);
        }

        if (oldPlays.isEmpty()) return false;

        currentPlays.addAll(oldPlays);
        return true;
    }
}
