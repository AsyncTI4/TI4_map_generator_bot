package ti4.commands.admin;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

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

        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game game : mapList.values()) {
            if (game.getName().startsWith("pbd")) {
                int vp = game.getVp();
                for (Player player : game.getPlayers().values()) {
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
                for (Player player : game.getPlayers().values()) {
                    int vpScore = player.getTotalVictoryPoints();
                    if (vp <= vpScore) {
                        String color = player.getColor();
                        String faction = player.getFaction();

                        winnerFactionCount.putIfAbsent(faction, 1);
                        winnerFactionCount.computeIfPresent(faction, (key, integer) -> integer + 1);

                        winnerColorCount.putIfAbsent(color, 1);
                        winnerColorCount.computeIfPresent(color, (key, integer) -> integer + 1);

                        findWinner = false;
                    }
                }
                if (findWinner) {
                    Date date = new Date(game.getLastModifiedDate());
                    Date currentDate = new Date();
                    long time_difference = currentDate.getTime() - date.getTime();
                    // Calculate time difference in days
                    long days_difference = (time_difference / (1000 * 60 * 60 * 24)) % 365;
                    if (days_difference > 30) {
                        int maxVP = game.getPlayers().values().stream().map(Player::getTotalVictoryPoints).max(Integer::compareTo).orElse(0);
                        if (game.getPlayers().values().stream().map(Player::getTotalVictoryPoints).filter(value -> value.equals(maxVP)).count() == 1) {
                            game.getPlayers().values().stream()
                                .filter(player -> player.getTotalVictoryPoints() == maxVP)
                                .findFirst()
                                .ifPresent(player -> {
                                    String color = player.getColor();
                                    String faction = player.getFaction();

                                    winnerFactionCount.putIfAbsent(faction, 1);
                                    winnerFactionCount.computeIfPresent(faction, (key, integer) -> integer + 1);

                                    winnerColorCount.putIfAbsent(color, 1);
                                    winnerColorCount.computeIfPresent(color, (key, integer) -> integer + 1);
                                });
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
            .forEach(entry -> sb.append(Emojis.getFactionIconFromDiscord(entry.getKey())).append(" - ").append(entry.getValue()).append("\n"));
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
