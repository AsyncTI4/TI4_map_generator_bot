package ti4.service.statistics.game;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class AllNamesStatisticsService {

    static void sendAllNames(SlashCommandInteractionEvent event) {
        StringBuilder names = new StringBuilder();
        AtomicInteger count = new AtomicInteger();

        GamesPage.consumeAllGames(GameStatisticsFilterer.getGamesFilter(event), game -> getName(game, count, names));

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Game Names", names.toString());
    }

    private static void getName(Game game, AtomicInteger gameCount, StringBuilder names) {
        int num = gameCount.incrementAndGet();
        names.append(num).append(". ").append(game.getName());
        if (isNotBlank(game.getCustomName())) {
            names.append(" (").append(game.getCustomName()).append(")");
        }
        names.append("\n");
    }
}
