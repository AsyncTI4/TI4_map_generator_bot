package ti4.service.statistics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.message.MessageHelper;

@UtilityClass
public class ExportToCsvService {

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> exportToCsv(event));
    }

    private void exportToCsv(SlashCommandInteractionEvent event) {
        int playerCount = event.getOption(GameStatisticsFilterer.PLAYER_COUNT_FILTER, 6, OptionMapping::getAsInt);
        StringBuilder output = new StringBuilder(header(playerCount));

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> output.append(System.lineSeparator()).append(gameToCsv(game)),
                ExecutionLockType.READ);

        if (output.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No games found matching filter.");
            return;
        }

        File outputCSV = new File("output.csv");
        try (PrintWriter pw = new PrintWriter(outputCSV, StandardCharsets.UTF_8)) {
            pw.print(output);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Something broke. Ping jazz");
            return;
        }

        long maxFileSize = event.getGuild() != null ? event.getGuild().getMaxFileSize() : 25L * 1024 * 1024;
        if (outputCSV.length() <= maxFileSize) {
            MessageHelper.sendFileToChannel(event.getChannel(), outputCSV);
            return;
        }

        try {
            File outputZip = zipCsv(outputCSV);
            if (outputZip.length() <= maxFileSize) {
                MessageHelper.sendFileToChannel(event.getChannel(), outputZip);
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "The exported data is too large to send, even compressed. Please narrow your filter (e.g. by player count or game type) and try again.");
            }
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Something broke. Ping jazz");
        }
    }

    private static File zipCsv(File csv) throws IOException {
        File zip = new File("output.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip));
                FileInputStream fis = new FileInputStream(csv)) {
            zos.putNextEntry(new ZipEntry(csv.getName()));
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
            zos.closeEntry();
        }
        return zip;
    }

    private static String header(int players) {
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

    private static String gameToCsv(Game game) {
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
            fields.add(Integer.toString(
                    p.getTotalVictoryPoints() - p.getSecretVictoryPoints() - p.getPublicVictoryPoints(false)));
            fields.add(String.join("|", p.getTechs()));
            fields.add(String.join("|", p.getRelics()));
            fields.add(Boolean.toString(p.getLeaderByType("hero").isEmpty()));
        }

        List<String> outputFields =
                fields.stream().map(f -> f.contains(",") ? "\"" + f + "\"" : f).toList();
        return String.join(",", outputFields);
    }
}
