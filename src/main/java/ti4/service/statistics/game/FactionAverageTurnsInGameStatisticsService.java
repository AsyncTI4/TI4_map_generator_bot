package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GamesPage;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.statistics.FactionStatisticsHelper;

@UtilityClass
class FactionAverageTurnsInGameStatisticsService {

    static void averageTurnsInAGameByFaction(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionCount = new HashMap<>();
        Map<String, Integer> factionTurnCount = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> averageTurnsInAGameByFaction(game, factionCount, factionTurnCount));

        StringBuilder sb = new StringBuilder();
        sb.append("Average Turns per Faction:").append("\n");
        Integer allFactionGames = factionCount.remove("allFactions");
        Integer allFactionTurns = factionTurnCount.remove("allFactions");
        if (allFactionGames != null && allFactionTurns != null) {
            sb.append("All Factions Combined:")
                    .append(String.format("%.2f", allFactionTurns / (double) allFactionGames))
                    .append('\n');
        }
        factionCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .filter(entry -> Mapper.getFaction(entry.getKey()) != null)
                .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
                .forEach(entry -> sb.append('`')
                    .append(StringUtils.leftPad(
                        String.format(
                            "%.2f",
                            (factionTurnCount.get(entry.getKey().getAlias()) / (double) entry.getValue())),
                        4))
                    .append(" turns from ")
                    .append(entry.getValue())
                    .append(" games`")
                    .append(entry.getKey().getFactionEmoji())
                    .append(' ')
                    .append(entry.getKey().getFactionNameWithSourceEmoji())
                    .append('\n'));

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Average Turns per Faction", sb.toString());
    }

    private static void averageTurnsInAGameByFaction(
            Game game, Map<String, Integer> factionCount, Map<String, Integer> factionTurnCount) {
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

    private static void updateStatistics(
            String faction, int turnCount, Map<String, Integer> factionCount, Map<String, Integer> factionTurnCount) {
        FactionStatisticsHelper.incrementFactionsIntValue(factionCount, faction);
        FactionStatisticsHelper.incrementFactionsIntValue(factionTurnCount, faction, turnCount);
    }
}
