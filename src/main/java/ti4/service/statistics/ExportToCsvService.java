package ti4.service.statistics;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
public class ExportToCsvService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> exportToCsv(event));
    }

    private void exportToCsv(SlashCommandInteractionEvent event) {
        int playerCount = event.getOption(GameStatisticsFilterer.PLAYER_COUNT_FILTER, 6, OptionMapping::getAsInt);
        StringBuilder output = new StringBuilder(header(playerCount));

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> output.append(System.lineSeparator()).append(gameToCsv(game))
        );

        if (output.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No games found matching filter.");
            return;
        }

        File outputCSV = new File("output.csv");
        try (PrintWriter pw = new PrintWriter(outputCSV)) {
            pw.print(output);
            pw.close();
            MessageHelper.sendFileToChannel(event.getChannel(), outputCSV);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Something broke. Ping jazz");
        }
    }

    public static String header(int players) {
        List<String> fields = new ArrayList<>();
        fields.add("game name");
        fields.add("playerCount");
        fields.add("victoryPointLimit");
        fields.add("allObjectives");
        for (int i = 1; i <= players; i++) {
            fields.add("player" + i + " faction");
            fields.add("player" + i + " color");
            fields.add("player" + i + " turnCount");
            fields.add("player" + i + " score");
            fields.add("player" + i + " SO points");
            fields.add("player" + i + " PO points");
            fields.add("player" + i + " Other points");
            fields.add("player" + i + " techs");
            fields.add("player" + i + " relics");
            fields.add("player" + i + " heroPurged");
        }
        return String.join(",", fields);
    }

    public static String gameToCsv(Game game) {
        List<String> fields = new ArrayList<>();
        fields.add(game.getName());
        fields.add(Integer.toString(game.getRealAndEliminatedAndDummyPlayers().size()));
        fields.add(Integer.toString(game.getVp()));
        fields.add(String.join("|", game.getRevealedPublicObjectives().keySet()));

        for (Player p : game.getRealAndEliminatedAndDummyPlayers()) {
            fields.add(p.getFaction());
            fields.add(p.getColor());
            fields.add(Integer.toString(p.getInRoundTurnCount()));
            fields.add(Integer.toString(p.getTotalVictoryPoints()));
            fields.add(Integer.toString(p.getSecretVictoryPoints()));
            fields.add(Integer.toString(p.getPublicVictoryPoints(false)));
            fields.add(Integer.toString(p.getTotalVictoryPoints() - p.getSecretVictoryPoints() - p.getPublicVictoryPoints(false)));
            fields.add(String.join("|", p.getTechs()));
            fields.add(String.join("|", p.getRelics()));
            fields.add(Boolean.toString(p.getLeaderByType("hero").isEmpty()));
        }

        List<String> outputFields = fields.stream().map(f -> f.contains(",") ? "\"" + f + "\"" : f).toList();
        return String.join(",", outputFields);
    }
}
