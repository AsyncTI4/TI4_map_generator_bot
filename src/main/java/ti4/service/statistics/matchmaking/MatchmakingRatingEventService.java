package ti4.service.statistics.matchmaking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.persistence.GameEntity;
import ti4.spring.persistence.PlayerEntity;
import ti4.spring.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class MatchmakingRatingEventService {

    private static final int MAX_LIST_SIZE = 50;

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void calculateRatings(SlashCommandInteractionEvent event) {
        boolean onlyTiglGames = event.getOption("tigl_only", false, OptionMapping::getAsBoolean);
        boolean showRating = event.getOption("show_my_rating", false, OptionMapping::getAsBoolean);

        List<PlayerEntity> players =
                playerEntityRepository.findAllWithUsersAndGamesByCompletedSixPlayerNonAllianceGame(onlyTiglGames);
        List<MatchmakingGame> games = getMatchmakingGames(players);

        List<MatchmakingRating> playerRatings = TrueSkillMatchmakingRatingService.calculateRatings(games);
        sendMessage(event, playerRatings, showRating);
    }

    private List<MatchmakingGame> getMatchmakingGames(List<PlayerEntity> players) {
        Map<GameEntity, List<PlayerEntity>> gamePlayers = new LinkedHashMap<>();
        for (PlayerEntity player : players) {
            gamePlayers
                    .computeIfAbsent(player.getGame(), game -> new ArrayList<>())
                    .add(player);
        }

        return gamePlayers.entrySet().stream()
                .map(entry -> toMatchmakingGame(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private static MatchmakingGame toMatchmakingGame(GameEntity game, List<PlayerEntity> players) {
        List<MatchmakingPlayer> matchmakingPlayers = players.stream()
                .map(player -> {
                    int rank = calculatePlayerRank(player.isWinner(), game.getVictoryPointGoal(), player.getScore());
                    return new MatchmakingPlayer(
                            player.getUser().getId(), player.getUser().getName(), rank);
                })
                .toList();
        long endedDate = game.getEndedEpochMilliseconds();
        return new MatchmakingGame(game.getGameName(), endedDate, matchmakingPlayers);
    }

    private static int calculatePlayerRank(boolean isWinner, int gameVictoryPointGoal, int playerScore) {
        if (isWinner) {
            return 1;
        }
        int pointsAwayFromVictory = gameVictoryPointGoal - playerScore;
        if (pointsAwayFromVictory <= 3) {
            return 2;
        }
        return 3 + pointsAwayFromVictory;
    }

    private static void sendMessage(
            SlashCommandInteractionEvent event, List<MatchmakingRating> playerRatings, boolean showRating) {
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
        String formattedString = String.format("""

                This list only includes the top %d players with a high confidence in their rating.

                The average rating of the player base is `%.3f`
                """, maxListSize, averageRating);
        sb.append(formattedString);

        playerRatings.stream()
                .filter(playerRating ->
                        playerRating.userId().equals(event.getUser().getId()))
                .findFirst()
                .ifPresent(playerRating -> {
                    if (showRating && playerRating.calibrationPercent() == 100) {
                        sb.append(String.format("\nYour rating is `%.3f`.", playerRating.rating()));
                    } else {
                        sb.append(String.format(
                                "\nWe are `%.1f%%` of the way to a high confidence in your rating.",
                                playerRating.calibrationPercent()));
                        if (showRating) {
                            sb.append(" We cannot show your rating until it reaches high confidence.");
                        }
                    }
                });

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Player Matchmaking Ratings", sb.toString());
    }

    public static MatchmakingRatingEventService getBean() {
        return SpringContext.getBean(MatchmakingRatingEventService.class);
    }
}
