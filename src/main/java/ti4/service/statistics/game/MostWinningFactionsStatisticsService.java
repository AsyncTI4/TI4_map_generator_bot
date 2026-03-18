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
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.service.statistics.FactionStatisticsHelper;

@UtilityClass
class MostWinningFactionsStatisticsService {

    static void getMostWinningFactions(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionToWinCount = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilterForWonGame(event),
                game -> countFactionWins(game, factionToWinCount));

        StringBuilder sb = new StringBuilder();
        sb.append("Wins per Faction:").append("\n");
        factionToWinCount.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
                .forEach(entry -> sb.append("`")
                        .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                        .append("x` ")
                        .append(entry.getKey().getFactionEmoji())
                        .append(" ")
                        .append(entry.getKey().getFactionNameWithSourceEmoji())
                        .append("\n"));

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Wins per Faction", sb.toString());
    }

    private void countFactionWins(Game game, Map<String, Integer> factionToWinCount) {
        game.getWinners()
                .forEach(player ->
                        FactionStatisticsHelper.incrementFactionsIntValue(factionToWinCount, player.getFaction()));
    }
}
