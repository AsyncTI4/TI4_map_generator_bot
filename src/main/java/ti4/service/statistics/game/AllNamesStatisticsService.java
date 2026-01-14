package ti4.service.statistics.game;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.map.Game;
import ti4.map.helper.GameHelper;
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
        names.append(" ").append(getSetupTimestamp(game));
        names.append("\n");
    }

    private static long getSetupTimestamp(Game game) {
        LocalDate localDate;
        try {
            localDate = GameHelper.getCreationDateAsLocalDate(game);
        } catch (DateTimeParseException e) {
            localDate = LocalDate.now();
        }

        int gameNameHash = game.getName().hashCode();
        int hours = Math.floorMod(gameNameHash, 24);
        int minutes = Math.floorMod(gameNameHash, 60);

        int customNameHash = game.getCustomName().hashCode();
        int seconds = Math.floorMod(customNameHash, 60);

        var localDateTime = localDate.atTime(hours, minutes, seconds);
        return localDateTime.atZone(ZoneId.of("UTC")).toInstant().getEpochSecond();
    }
}
