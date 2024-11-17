package ti4.commands2.statistics;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands2.Subcommand;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ExportToCSV extends Subcommand {

    public static final String EXPORT = "export_games_to_csv";

    public ExportToCSV() {
        super(EXPORT, "Export game data to a CSV file");
        addOptions(GameStatisticFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Game> games = GameStatisticFilterer.getFilteredGames(event);
        if (games.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No games found matching filter.");
            return;
        }

        int playerCount = event.getOption(GameStatisticFilterer.PLAYER_COUNT_FILTER, 6, OptionMapping::getAsInt);
        StringBuilder output = new StringBuilder(header(playerCount));
        for (Game game : games) {
            output.append(System.lineSeparator()).append(gameToCsv(game));
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
            fields.add(Integer.toString(p.getTurnCount()));
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
