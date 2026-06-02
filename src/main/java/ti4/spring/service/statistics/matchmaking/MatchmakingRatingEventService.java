package ti4.spring.service.statistics.matchmaking;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.PlayerEntityRepository;

@Service
@RequiredArgsConstructor
public class MatchmakingRatingEventService {

    private static final int MAX_LIST_SIZE = 50;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void calculateRatings(SlashCommandInteractionEvent event) {
        boolean onlyTiglGames = event.getOption("tigl_only", Boolean.FALSE, OptionMapping::getAsBoolean);
        boolean showRating = event.getOption("show_my_rating", Boolean.FALSE, OptionMapping::getAsBoolean);
        boolean conservativeRating = event.getOption("conservative", Boolean.FALSE, OptionMapping::getAsBoolean);

        List<MatchmakingRating> playerRatings = getPlayerRatings(onlyTiglGames, conservativeRating);
        sendMessage(event, playerRatings, showRating, conservativeRating);
    }

    Map<String, BigDecimal> getPlayerRatings(Set<String> userIds) {
        return getPlayerRatings(false).stream()
                .filter(matchmakingRating -> userIds.contains(matchmakingRating.userId()))
                .collect(Collectors.toMap(MatchmakingRating::userId, MatchmakingRating::rating));
    }

    public List<MatchmakingRating> getPlayerRatings(boolean onlyTiglGames) {
        return getPlayerRatings(onlyTiglGames, false);
    }

    public List<MatchmakingRating> getPlayerRatings(boolean onlyTiglGames, boolean useConservativeRating) {
        List<PlayerEntity> players =
                playerEntityRepository.findAllWithUsersAndGamesByCompletedSixPlayerNonAllianceGame(onlyTiglGames);
        List<MatchmakingGame> games = MatchmakingGame.getMatchmakingGames(players);
        return TrueSkillMatchmakingRatingService.calculateRatings(games, useConservativeRating);
    }

    private static void sendMessage(
            SlashCommandInteractionEvent event,
            List<MatchmakingRating> playerRatings,
            boolean showRating,
            boolean conservativeRating) {
        int maxListSize = Math.min(MAX_LIST_SIZE, playerRatings.size());
        StringBuilder sb = new StringBuilder();
        String ratingLabel = conservativeRating ? "Conservative Rating" : "Rating";
        sb.append("__**Player Matchmaking Ratings:**__\n");
        for (int i = 0, listSize = 0; i < playerRatings.size() && listSize < maxListSize; i++) {
            var playerRating = playerRatings.get(i);
            if (playerRating.calibrationPercent().compareTo(ONE_HUNDRED) < 0) {
                continue;
            }
            listSize++;
            String formattedString = String.format(
                    "%d. `%s` `%s=%.3f`\n", listSize, playerRating.username(), ratingLabel, playerRating.rating());
            sb.append(formattedString);
        }

        BigDecimal averageRating = playerRatings.isEmpty()
                ? BigDecimal.ZERO
                : playerRatings.stream()
                        .map(MatchmakingRating::rating)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(playerRatings.size()), java.math.MathContext.DECIMAL64);
        String formattedString = String.format("""

                This list only includes the top %d players with a high confidence in their rating.

                The average %s of the player base is `%.3f`
                """, maxListSize, ratingLabel.toLowerCase(), averageRating);
        sb.append(formattedString);

        playerRatings.stream()
                .filter(playerRating ->
                        playerRating.userId().equals(event.getUser().getId()))
                .findFirst()
                .ifPresent(playerRating -> {
                    if (showRating && playerRating.calibrationPercent().compareTo(ONE_HUNDRED) == 0) {
                        sb.append(String.format(
                                "\nYour %s is `%.3f`.", ratingLabel.toLowerCase(), playerRating.rating()));
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

    public static MatchmakingRatingEventService get() {
        return SpringContext.getBean(MatchmakingRatingEventService.class);
    }
}
