package ti4.service.statistics.player;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingEventService;

@UtilityClass
class PlayerAverageOpponentMmrStatisticsService {

    private static final BigDecimal DEFAULT_RATING = BigDecimal.valueOf(20);

    void showPlayerAverageOpponentMmr(SlashCommandInteractionEvent event) {
        List<GamePlayers> games = new ArrayList<>();
        Map<String, String> participantIdToUsername = new HashMap<>();
        Set<String> allRatingUserIds = new HashSet<>();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> {
                    List<Player> realPlayers = game.getRealAndEliminatedPlayers();
                    if (realPlayers.size() < 2) return;

                    List<String> ratingUserIds = new ArrayList<>();
                    List<String> participantIds = new ArrayList<>();
                    realPlayers.forEach(player -> {
                        ratingUserIds.add(player.getUserID());
                        String statsId = player.getStatsTrackedUserID();
                        participantIds.add(statsId);
                        participantIdToUsername.put(statsId, player.getStatsTrackedUserName());
                    });
                    games.add(new GamePlayers(ratingUserIds, participantIds));
                    allRatingUserIds.addAll(ratingUserIds);
                },
                ExecutionLockType.READ);

        Map<String, BigDecimal> ratings =
                MatchmakingRatingEventService.get().getConservativePlayerRatings(allRatingUserIds);

        Map<String, BigDecimal> playerOpponentMmrSum = new HashMap<>();
        Map<String, Integer> playerGameCount = new HashMap<>();
        for (GamePlayers game : games) {
            int size = game.ratingUserIds().size();
            BigDecimal total = game.ratingUserIds().stream()
                    .map(id -> ratings.getOrDefault(id, DEFAULT_RATING))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal opponentCount = BigDecimal.valueOf(size - 1L);
            for (int i = 0; i < size; i++) {
                BigDecimal ownRating = ratings.getOrDefault(game.ratingUserIds().get(i), DEFAULT_RATING);
                BigDecimal opponentAverage = total.subtract(ownRating).divide(opponentCount, MathContext.DECIMAL64);
                String statsId = game.participantIds().get(i);
                playerOpponentMmrSum.merge(statsId, opponentAverage, BigDecimal::add);
                playerGameCount.merge(statsId, 1, Integer::sum);
            }
        }

        int maximumListedPlayers = event.getOption("max_list_size", 50, OptionMapping::getAsInt);
        int minimumGameCountFilter = event.getOption("min_game_count", 10, OptionMapping::getAsInt);
        List<Map.Entry<String, BigDecimal>> entries = playerGameCount.entrySet().stream()
                .filter(entry -> entry.getValue() >= minimumGameCountFilter)
                .map(entry -> Map.entry(
                        entry.getKey(),
                        playerOpponentMmrSum
                                .get(entry.getKey())
                                .divide(BigDecimal.valueOf(entry.getValue()), MathContext.DECIMAL64)))
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("__**Player Average Opponent MMR:**__").append('\n');
        if (entries.isEmpty()) {
            sb.append("No players found for the given filters!");
        }
        for (int i = 0; i < entries.size() && i < maximumListedPlayers; i++) {
            Map.Entry<String, BigDecimal> entry = entries.get(i);
            sb.append(i + 1)
                    .append(". `")
                    .append(StringUtils.leftPad(participantIdToUsername.get(entry.getKey()), 4))
                    .append("` ")
                    .append(String.format("%.3f", entry.getValue()))
                    .append(" (")
                    .append(playerGameCount.get(entry.getKey()))
                    .append(" games)")
                    .append('\n');
        }

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Player Average Opponent MMR", sb.toString());
    }

    private record GamePlayers(List<String> ratingUserIds, List<String> participantIds) {}
}
