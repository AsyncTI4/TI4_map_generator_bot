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
import ti4.map.GamesPage;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.ColorEmojis;

@UtilityClass
class MostPlayerColorService {

    public static void getMostPlayedColour(SlashCommandInteractionEvent event) {
        Map<String, Integer> colorCount = new HashMap<>();

        GamesPage.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event), game -> getMostPlayedColor(game, colorCount));

        StringBuilder sb = new StringBuilder();
        sb.append("Plays per Colour:").append("\n");
        colorCount.entrySet().stream()
                .filter(e -> Mapper.isValidColor(e.getKey()))
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> sb.append("`")
                        .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                        .append("x` ")
                        .append(ColorEmojis.getColorEmojiWithName(entry.getKey()))
                        .append("\n"));
        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Plays per Colour", sb.toString());
    }

    private static void getMostPlayedColor(Game game, Map<String, Integer> colorCount) {
        for (Player player : game.getRealPlayers()) {
            String color = player.getColor();
            colorCount.put(color, 1 + colorCount.getOrDefault(color, 0));
        }
    }
}
