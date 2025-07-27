package ti4.service.statistics.game;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;

@UtilityClass
class GameCountStatisticsService {

    public static void getGameCount(SlashCommandInteractionEvent event) {
        AtomicInteger count = new AtomicInteger();

        GamesPage.consumeAllGames(
            GameStatisticsFilterer.getGamesFilter(event),
            game -> count.getAndIncrement()
        );

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Game count: " + count.get());
    }
}
