package ti4.service.statistics.matchmaking;

import static java.util.function.Predicate.not;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.commands.statistics.GameStatisticsFilterer;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.persistence.GamesPage;
import ti4.message.MessageHelper;
import ti4.service.statistics.StatisticsPipeline;

@UtilityClass
public class MatchmakingRatingEventService {

    private static final int MAX_LIST_SIZE = 50;

    public void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> calculateRatings(event));
    }

    private static void calculateRatings(SlashCommandInteractionEvent event) {
        List<MatchmakingGame> games = new ArrayList<>();
        boolean onlyTiglGames = event.getOption(Constants.TIGL_GAME, false, OptionMapping::getAsBoolean);
        Predicate<Game> filter = GameStatisticsFilterer.getFinishedGamesFilter(6, null).and(not(Game::isAllianceMode));
        if (onlyTiglGames) {
            filter = filter.and(Game::isCompetitiveTIGLGame);
        }
        GamesPage.consumeAllGames(filter, game -> games.add(MatchmakingGame.from(game)));

        List<MatchmakingRating> playerRatings = MatchmakingRatingService.calculateRatings(games);
        sendMessage(event, playerRatings);
    }

    private static void sendMessage(SlashCommandInteractionEvent event, List<MatchmakingRating> playerRatings) {
        int maxListSize = Math.min(MAX_LIST_SIZE, playerRatings.size());
        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Matchmaking Ratings:**__\n");
        for (int i = 0, listSize = 0; i < playerRatings.size() && listSize < maxListSize; i++) {
            var playerRating = playerRatings.get(i);
            if (playerRating.calibrationPercent() < 100) {
                continue;
            }
            listSize++;
            String formattedString =
                    String.format("%d. `%s` `Rating=%.3f`\n", listSize, playerRating.username(), playerRating.rating());
            sb.append(formattedString);
        }

        double averageRating = playerRatings.stream()
                .mapToDouble(MatchmakingRating::rating)
                .average()
                .orElse(Double.NaN);
        String formattedString = String.format(
                """

                This list only includes the top %d players with a high confidence in their rating.

                The average rating of the player base is `%.3f`
                """,
                maxListSize, averageRating);
        sb.append(formattedString);

        playerRatings.stream()
                .filter(playerRating ->
                        playerRating.userId().equals(event.getUser().getId()))
                .findFirst()
                .ifPresent(playerRating -> sb.append(String.format(
                        "\nWe are `%.1f%%` of the way to a high confidence in your rating.",
                        Math.min(100, playerRating.calibrationPercent()))));

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Player Matchmaking Ratings", sb.toString());
    }
}
