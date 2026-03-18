package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.List;
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
import ti4.model.FactionModel;
import ti4.service.statistics.FactionStatisticsHelper;

@UtilityClass
class FactionWinPercentStatisticsService {

    static void getFactionWinPercent(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> getFactionWinPercent(game, factionWinCount, factionGameCount));

        StringBuilder sb = new StringBuilder();
        sb.append("Faction Win Percent:").append("\n");
        factionGameCount.keySet().stream()
                .map(integer -> {
                    double winCount = factionWinCount.getOrDefault(integer, 0);
                    double gameCount = factionGameCount.getOrDefault(integer, 0);
                    return Map.entry(integer, gameCount == 0 ? 0 : Math.round(100 * winCount / gameCount));
                })
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(entry -> {
                    FactionModel factionModel = Mapper.getFaction(entry.getKey());
                    String factionEmoji = factionModel != null ? factionModel.getFactionEmoji() : "\uD83D\uDC7B";
                    String factionName = factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : entry.getKey();
                    sb.append("`")
                            .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                            .append("%` (")
                            .append(factionGameCount.get(entry.getKey()))
                            .append(" games) ")
                            .append(factionEmoji)
                            .append(" ")
                            .append(factionName)
                            .append("\n");
                });

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Faction Win Percent", sb.toString());
    }

    private static void getFactionWinPercent(
            Game game, Map<String, Integer> factionWinCount, Map<String, Integer> factionGameCount) {
        List<Player> winners = game.getWinners();
        if (winners.isEmpty()) {
            return;
        }

        for (Player winner : winners) {
            String winningFaction = winner.getFaction();

            FactionStatisticsHelper.incrementFactionsIntValue(factionWinCount, winningFaction);
            FactionStatisticsHelper.incrementFactionsIntValue(factionWinCount, "allWinners");
        }

        game.getRealAndEliminatedAndDummyPlayers()
                .forEach(player ->
                        FactionStatisticsHelper.incrementFactionsIntValue(factionGameCount, player.getFaction()));
    }
}
