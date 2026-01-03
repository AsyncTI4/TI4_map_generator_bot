package ti4.commands.developer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.website.model.stats.GameStatsDashboardPayload;

class RunAgainstAllGames extends Subcommand {

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Map<Long, List<String>> creationDateTimeToGames = new HashMap<>();
        Map<Long, List<String>> setupTimestampToGames = new HashMap<>();
        GamesPage.consumeAllGames(game -> {
            long creationDateTime = game.getCreationDateTime();
            creationDateTimeToGames
                    .computeIfAbsent(creationDateTime, key -> new ArrayList<>())
                    .add(formatGameLabel(game));

            long setupTimestamp = new GameStatsDashboardPayload(game).getSetupTimestamp();
            setupTimestampToGames
                    .computeIfAbsent(setupTimestamp, key -> new ArrayList<>())
                    .add(formatGameLabel(game));
        });

        List<String> creationDateTimeOverlaps = formatOverlaps("creationDateTime", creationDateTimeToGames);
        List<String> setupTimestampOverlaps = formatOverlaps("setupTimestamp", setupTimestampToGames);

        BotLogger.info(String.join(System.lineSeparator(), creationDateTimeOverlaps));
        BotLogger.info(String.join(System.lineSeparator(), setupTimestampOverlaps));

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games. "
                + "creationDateTime overlaps: " + (creationDateTimeOverlaps.size() - 1)
                + ", setupTimestamp overlaps: " + (setupTimestampOverlaps.size() - 1) + ".");
    }

    private static List<String> formatOverlaps(String label, Map<Long, List<String>> timestampToGames) {
        List<String> overlaps = timestampToGames.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> label + " overlap at " + entry.getKey() + ": " + String.join(", ", entry.getValue()))
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add("Found " + overlaps.size() + " " + label + " overlap(s).");
        lines.addAll(overlaps);
        return lines;
    }

    private static String formatGameLabel(Game game) {
        return game.getName() + " (" + game.getID() + ")";
    }
}
