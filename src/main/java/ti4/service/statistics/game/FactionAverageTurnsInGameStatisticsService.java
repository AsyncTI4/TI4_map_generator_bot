package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class FactionAverageTurnsInGameStatisticsService {

    public static void averageTurnsInAGameByFaction(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionCount = new HashMap<>();
        Map<String, Integer> factionTurnCount = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> averageTurnsInAGameByFaction(game, factionCount, factionTurnCount));

        StringBuilder sb = new StringBuilder();
        sb.append("Average Turns per Faction:").append("\n");
        sb.append("All Factions Combined:").append(String.format("%.2f", factionTurnCount.get("allFactions") / (double) factionCount.get("allFactions"))).append("\n");
        factionCount.entrySet().stream()
            .filter(entry -> Mapper.isValidFaction(entry.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(String.format("%.2f", (factionTurnCount.get(entry.getKey().getAlias()) / (double) entry.getValue())), 4))
                .append(" turns from ").append(entry.getValue()).append(" games`")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Average Turns per Faction", sb.toString());
    }

    private static void averageTurnsInAGameByFaction(Game game, Map<String, Integer> factionCount, Map<String, Integer> factionTurnCount) {
        for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
            String faction = player.getFaction();
            int turnCount = player.getNumberOfTurns() - game.getDiscardAgendas().size() - game.getRound();
            if (turnCount < 10 || turnCount > 200) {
                continue;
            }
            updateStatistics(faction, turnCount, factionCount, factionTurnCount);
            updateStatistics("allFactions", turnCount, factionCount, factionTurnCount);
        }
    }

    private static void updateStatistics(String faction, int turnCount, Map<String, Integer> factionCount, Map<String, Integer> factionTurnCount) {
        factionCount.put(faction, factionCount.getOrDefault(faction, 0) + 1);
        factionTurnCount.put(faction, factionTurnCount.getOrDefault(faction, 0) + turnCount);
    }
}
