package ti4.service.statistics.game;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.message.MessageHelper;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingEventService;

@UtilityClass
class AverageGameMmrStatisticsService {

    private static final BigDecimal DEFAULT_RATING = BigDecimal.valueOf(20);
    private static final int MAX_LIST_SIZE = 100;

    static void showAverageGameMmr(SlashCommandInteractionEvent event) {
        Map<String, List<String>> gamePlayerIds = new LinkedHashMap<>();
        Map<String, String> gameCustomNames = new HashMap<>();
        Set<String> allUserIds = new HashSet<>();

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getGamesFilter(event),
                game -> {
                    List<String> ids = game.getRealAndEliminatedPlayers().stream()
                            .map(Player::getUserID)
                            .toList();
                    if (ids.isEmpty()) {
                        return;
                    }
                    gamePlayerIds.put(game.getName(), ids);
                    gameCustomNames.put(game.getName(), game.getCustomName());
                    allUserIds.addAll(ids);
                },
                ExecutionLockType.READ);

        Map<String, BigDecimal> ratings = MatchmakingRatingEventService.get().getConservativePlayerRatings(allUserIds);

        String runnerUserId = event.getUser().getId();

        List<Map.Entry<String, BigDecimal>> gameAverages = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : gamePlayerIds.entrySet()) {
            List<String> ids = entry.getValue();
            BigDecimal average = ids.stream()
                    .map(id -> ratings.getOrDefault(id, DEFAULT_RATING))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(ids.size()), MathContext.DECIMAL64);
            gameAverages.add(Map.entry(entry.getKey(), average));
        }
        gameAverages.sort(Map.Entry.<String, BigDecimal>comparingByValue().reversed());

        StringBuilder sb = new StringBuilder("__**Average MMR per game:**__\n");
        int index = 0;
        for (Map.Entry<String, BigDecimal> entry : gameAverages) {
            if (index >= MAX_LIST_SIZE) {
                break;
            }
            index++;
            String gameName = entry.getKey();
            sb.append(String.format("%d. `%s`", index, gameName));
            String customName = gameCustomNames.get(gameName);
            if (isNotBlank(customName)) {
                sb.append(String.format(" `%s`", customName));
            }
            sb.append(String.format(" (rated %d)", MatchmakingRatingEventService.toDisplayRating(entry.getValue())));
            if (gamePlayerIds.get(gameName).contains(runnerUserId)) {
                sb.append("  👀 **YOU!**");
            }
            sb.append(System.lineSeparator());
        }

        if (gameAverages.isEmpty()) {
            sb.append("No games matched the given filters.\n");
        } else {
            BigDecimal overallAverage = gameAverages.stream()
                    .map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(gameAverages.size()), MathContext.DECIMAL64);
            sb.append(String.format(
                    "%nThe average MMR across all games is `%d`.%n",
                    MatchmakingRatingEventService.toDisplayRating(overallAverage)));
        }

        MessageHelper.sendMessageToThread(
                (MessageChannelUnion) event.getMessageChannel(), "Average MMR", sb.toString());
    }
}
