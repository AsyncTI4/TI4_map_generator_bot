package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
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

        HashMap<String, Integer> factionCount = new HashMap<>();
        HashMap<String, Integer> winnerFactionCount = new HashMap<>();
        HashMap<String, Integer> colorCount = new HashMap<>();
        HashMap<String, Integer> winnerColorCount = new HashMap<>();

        BufferedImage fakeImage = new BufferedImage(5, 5, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = fakeImage.getGraphics();

        HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
        for (Map map : mapList.values()) {
            if (map.getName().startsWith("pbd")) {
                int vp = map.getVp();
                HashMap<Player, Integer> userVPs = new HashMap<>();
                GenerateMap.objectives(map, 0, graphics, userVPs, false);
                for (Player player : map.getPlayers().values()) {
                    String color = player.getColor();
                    String faction = player.getFaction();
                    if (faction != null && color != null && !faction.isEmpty() && !faction.equals("null")) {
                        factionCount.putIfAbsent(faction, 1);
                        factionCount.computeIfPresent(faction, (key, integer) -> integer + 1);

                        colorCount.putIfAbsent(color, 1);
                        colorCount.computeIfPresent(color, (key, integer) -> integer + 1);
                    }
                }
                boolean findWinner = true;
                for (java.util.Map.Entry<Player, Integer> entry : userVPs.entrySet()) {
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
                    Date date = new Date(map.getLastModifiedDate());
                    Date currentDate = new Date();
                    long time_difference = currentDate.getTime() - date.getTime();
                    // Calucalte time difference in days
                    long days_difference = (time_difference / (1000 * 60 * 60 * 24)) % 365;
                    if (days_difference > 30) {
                        Integer maxVP = userVPs.values().stream().max(Integer::compareTo).orElse(0);
                        if (userVPs.values().stream().filter(value -> value.equals(maxVP)).count() == 1) {
                            for (java.util.Map.Entry<Player, Integer> entry : userVPs.entrySet()) {
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

        MessageHelper.replyToMessageTI4Logo(event);

        sendStatistics(event, factionCount, "Faction played:");
        sendStatisticsColor(event, colorCount, "Color played:");
        sendStatistics(event, winnerFactionCount, "Winning Faction:");
        sendStatisticsColor(event, winnerColorCount, "Winning Color:");

    }

    private static void sendStatistics(SlashCommandInteractionEvent event, HashMap<String, Integer> factionCount, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append(text).append("\n");
        factionCount.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByValue())
                .forEach(entry -> sb.append(Helper.getFactionIconFromDiscord(entry.getKey())).append(" - ").append(entry.getValue()).append("\n"));
        MessageHelper.sendMessageToChannel(event, sb.toString());
    }

    private static void sendStatisticsColor(SlashCommandInteractionEvent event, HashMap<String, Integer> factionCount, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append(text).append("\n");
        factionCount.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByValue())
                .forEach(entry -> sb.append(entry.getKey()).append(" - ").append(entry.getValue()).append("\n"));
        MessageHelper.sendMessageToChannel(event, sb.toString());
    }

    private class Stats {
        private String faction;
        private String color;

        private int VPCount;
    }

}
