package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class MostWinningFactionsStatisticsService {

    public static void getMostWinningFactions(SlashCommandInteractionEvent event) {
        Map<String, Integer> factionToWinCount = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> countFactionWins(game, factionToWinCount)
        );

        StringBuilder sb = new StringBuilder();
        sb.append("Wins per Faction:").append("\n");
        factionToWinCount.entrySet().stream()
            .filter(entry -> Mapper.isValidFaction(entry.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("x` ")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Wins per Faction", sb.toString());
    }

    private void countFactionWins(Game game, Map<String, Integer> factionToWinCount) {
        game.getWinner()
            .map(Player::getFaction)
            .ifPresent(faction -> factionToWinCount.merge(faction, 1, Integer::sum));
    }
}
