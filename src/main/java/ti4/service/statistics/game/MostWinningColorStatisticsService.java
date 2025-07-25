package ti4.service.statistics.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;

@UtilityClass
class MostWinningColorStatisticsService {

    static void showMostWinningColor(SlashCommandInteractionEvent event) {
        Map<String, Integer> winnerColorCount = new HashMap<>();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilterForWonGame(event),
            game -> getWinningColor(game, winnerColorCount));

        StringBuilder sb = new StringBuilder();
        sb.append("Wins per Colour:").append("\n");
        winnerColorCount.entrySet().stream()
            .filter(e -> Mapper.isValidColor(e.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("x` ")
                .append(ColorEmojis.getColorEmojiWithName(entry.getKey()))
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Wins per Colour", sb.toString());
    }

    private static void getWinningColor(Game game, Map<String, Integer> winningColorCount) {
        Optional<Player> winner = game.getWinner();
        if (winner.isEmpty()) {
            return;
        }
        String winningColor = winner.get().getColor();
        winningColorCount.put(winningColor, 1 + winningColorCount.getOrDefault(winningColor, 0));
    }
}
