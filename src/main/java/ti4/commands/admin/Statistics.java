package ti4.commands.admin;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Date;
import java.util.HashMap;

public class Statistics extends AdminSubcommandData {

    public Statistics() {
        super(Constants.STATISTICS, "Statistics");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Map<String, Integer> factionCount = new HashMap<>();
        Map<String, Integer> winnerFactionCount = new HashMap<>();
        Map<String, Integer> colorCount = new HashMap<>();
        Map<String, Integer> winnerColorCount = new HashMap<>();

        BufferedImage fakeImage = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = fakeImage.getGraphics();

        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game activeGame : mapList.values()) {
            if (activeGame.getName().startsWith("pbd")) {
                int vp = activeGame.getVp();
                HashMap<Player, Integer> userVPs = new HashMap<>();
                GenerateMap.getInstance().objectives(activeGame, 0, graphics, userVPs, false);
                for (Player player : activeGame.getPlayers().values()) {
                    String color = player.getColor();
                    String faction = player.getFaction();
                    if (faction != null && color != null && !faction.isEmpty() && !"null".equals(faction)) {
                        factionCount.putIfAbsent(faction, 1);
                        factionCount.computeIfPresent(faction, (key, integer) -> integer + 1);

                        colorCount.putIfAbsent(color, 1);
                        colorCount.computeIfPresent(color, (key, integer) -> integer + 1);
                    }
                }
                boolean findWinner = true;
                for (Map.Entry<Player, Integer> entry : userVPs.entrySet()) {
                    Integer vpScore = entry.getValue();
                    if (vp <= vpScore) {
                        String color = entry.getKey().getColor();
                        String faction = entry.getKey().getFaction();

                        winnerFactionCount.putIfAbsent(faction, 1);
                        winnerFactionCount.computeIfPresent(faction, (key, integer) -> integer + 1);

                        winnerColorCount.putIfAbsent(color, 1);
                        winnerColorCount.computeIfPresent(color, (key, integer) -> integer + 1);

                        findWinner = false;
                    }
                }
                if (findWinner) {
                    Date date = new Date(activeGame.getLastModifiedDate());
                    Date currentDate = new Date();
                    long time_difference = currentDate.getTime() - date.getTime();
                    // Calculate time difference in days
                    long days_difference = (time_difference / (1000 * 60 * 60 * 24)) % 365;
                    if (days_difference > 30) {
                        Integer maxVP = userVPs.values().stream().max(Integer::compareTo).orElse(0);
                        if (userVPs.values().stream().filter(value -> value.equals(maxVP)).count() == 1) {
                            for (Map.Entry<Player, Integer> entry : userVPs.entrySet()) {
                                Integer vpScore = entry.getValue();
                                if (maxVP.equals(vpScore)) {
                                    String color = entry.getKey().getColor();
                                    String faction = entry.getKey().getFaction();

                                    winnerFactionCount.putIfAbsent(faction, 1);
                                    winnerFactionCount.computeIfPresent(faction, (key, integer) -> integer + 1);

                                    winnerColorCount.putIfAbsent(color, 1);
                                    winnerColorCount.computeIfPresent(color, (key, integer) -> integer + 1);
                                }
                            }
                        }
                    }

                }
            }
        }


        sendStatistics(event, factionCount, "Faction played:");
        sendStatisticsColor(event, colorCount, "Color played:");
        sendStatistics(event, winnerFactionCount, "Winning Faction:");
        sendStatisticsColor(event, winnerColorCount, "Winning Color:");

    }

    private static void sendStatistics(SlashCommandInteractionEvent event, Map<String, Integer> factionCount, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append(text).append("\n");
        factionCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> sb.append(Helper.getFactionIconFromDiscord(entry.getKey())).append(" - ").append(entry.getValue()).append("\n"));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    private static void sendStatisticsColor(SlashCommandInteractionEvent event, Map<String, Integer> factionCount, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append(text).append("\n");
        factionCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> sb.append(entry.getKey()).append(" - ").append(entry.getValue()).append("\n"));
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
