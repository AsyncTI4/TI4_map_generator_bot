package ti4.spring.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.persistence.PlayerEntity;
import ti4.spring.persistence.PlayerEntityRepository;
import ti4.spring.persistence.UserEntity;

@Service
@RequiredArgsConstructor
public class HitsPerTurnService {

    private static final int DEFAULT_PLAYER_LIMIT = 50;
    private static final int DEFAULT_MINIMUM_EXPECTED_HITS = 50;
    private static final int DEFAULT_MINIMUM_TURNS = 100;

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void getExpectedHitsPerTurn(SlashCommandInteractionEvent event) {
        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean sortOrderAscending = event.getOption("ascending", true, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, DEFAULT_PLAYER_LIMIT, OptionMapping::getAsInt);
        int minimumExpectedHits = event.getOption(
                Constants.MINIMUM_NUMBER_OF_EXPECTED_HITS, DEFAULT_MINIMUM_EXPECTED_HITS, OptionMapping::getAsInt);
        int minimumTurns =
                event.getOption(Constants.MINIMUM_NUMBER_OF_TURNS, DEFAULT_MINIMUM_TURNS, OptionMapping::getAsInt);

        List<PlayerEntity> players = ignoreEndedGames
                ? playerEntityRepository.findAllWithUsersByActiveGame()
                : playerEntityRepository.findAllWithUsers();

        Map<UserEntity, HitsPerTurnAccumulator> usersToAccumulators = getUsersToHitsPerTurnAccumulators(players);

        List<HitsPerTurnAccumulator> filteredResults = usersToAccumulators.values().stream()
                .filter(s -> s.expectedHits >= minimumExpectedHits && s.turns >= minimumTurns)
                .toList();

        Map<HitsPerTurnAccumulator, String> tiers = getTiers(filteredResults);
        List<HitsPerTurnAccumulator> sortedResults = filteredResults.stream()
                .sorted((a, b) -> sortOrderAscending
                        ? Double.compare(a.getExpectedHitsOutOfTurns(), b.getExpectedHitsOutOfTurns())
                        : Double.compare(b.getExpectedHitsOutOfTurns(), a.getExpectedHitsOutOfTurns()))
                .limit(topLimit)
                .toList();

        MessageHelper.sendMessageToThread(
                event.getChannel(), "Expected Hits Per Turn Record", toResultString(sortedResults, tiers));
    }

    @NotNull
    private static Map<UserEntity, HitsPerTurnAccumulator> getUsersToHitsPerTurnAccumulators(
            List<PlayerEntity> players) {
        Map<UserEntity, HitsPerTurnAccumulator> userToAccumulators = new HashMap<>();
        for (PlayerEntity player : players) {
            if (player.getExpectedHits() == 0 || player.getActualHits() == 0) continue;
            // ignore anomalies, like nearly infinite battles
            if (2 * player.getExpectedHits() >= player.getTotalNumberOfTurns()) continue;
            userToAccumulators
                    .computeIfAbsent(player.getUser(), user -> new HitsPerTurnAccumulator(user.getName()))
                    .addGame(player.getExpectedHits(), player.getTotalNumberOfTurns());
        }
        return userToAccumulators;
    }

    @Transactional(readOnly = true)
    public String getHitsPerTurn(List<String> userIds) {
        List<PlayerEntity> players = playerEntityRepository.findAllWithUsersByUserIdIn(userIds);

        Map<UserEntity, HitsPerTurnAccumulator> usersToAccumulators = getUsersToHitsPerTurnAccumulators(players);

        List<HitsPerTurnAccumulator> filteredResults = usersToAccumulators.values().stream()
                .filter(s -> s.expectedHits > 0 && s.turns > 0)
                .toList();

        Map<HitsPerTurnAccumulator, String> tiers = getTiers(filteredResults);
        List<HitsPerTurnAccumulator> sortedResults = filteredResults.stream()
                .sorted((a, b) -> Double.compare(b.getExpectedHitsOutOfTurns(), a.getExpectedHitsOutOfTurns()))
                .toList();

        return toResultString(sortedResults, tiers);
    }

    private String toResultString(
            List<HitsPerTurnAccumulator> accumulators, Map<HitsPerTurnAccumulator, String> tiers) {
        StringBuilder sb = new StringBuilder("## __**Expected Hits Per Turn**__\n");
        int index = 1;
        for (var accumulator : accumulators) {
            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index), 3))
                    .append(". ")
                    .append(String.format("%.2f", accumulator.getExpectedHitsOutOfTurns()))
                    .append("` ")
                    .append(accumulator.username)
                    .append(" **[")
                    .append(tiers.getOrDefault(accumulator, "NORMAL"))
                    .append("]**   [")
                    .append(String.format("%.1f", accumulator.expectedHits))
                    .append("/")
                    .append(accumulator.turns)
                    .append(" expected hits/turns]\n");
            index++;
        }
        return sb.toString();
    }

    private static Map<HitsPerTurnAccumulator, String> getTiers(List<HitsPerTurnAccumulator> accumulators) {
        List<HitsPerTurnAccumulator> ascendingResults = accumulators.stream()
                .sorted(Comparator.comparingDouble(HitsPerTurnAccumulator::getExpectedHitsOutOfTurns))
                .toList();
        int totalPlayers = ascendingResults.size();
        return IntStream.range(0, totalPlayers)
                .boxed()
                .collect(Collectors.toMap(ascendingResults::get, i -> getTier(i + 1, totalPlayers)));
    }

    private static String getTier(int index, int totalPlayers) {
        if (totalPlayers < 100) {
            return "UNKNOWN TIER";
        }
        double percentile = (double) index / totalPlayers;
        if (percentile <= 0.10) {
            return "VERY LOW";
        }
        if (percentile <= 0.30) {
            return "LOW";
        }
        if (percentile <= 0.70) {
            return "NORMAL";
        }
        if (percentile <= 0.90) {
            return "HIGH";
        }
        return "VERY HIGH";
    }

    public static HitsPerTurnService getBean() {
        return SpringContext.getBean(HitsPerTurnService.class);
    }

    private static class HitsPerTurnAccumulator {
        String username;
        double expectedHits;
        int turns;

        HitsPerTurnAccumulator(String username) {
            this.username = username;
        }

        void addGame(double expectedHits, int turns) {
            this.expectedHits += expectedHits;
            this.turns += turns;
        }

        double getExpectedHitsOutOfTurns() {
            return turns == 0 ? 0 : expectedHits / turns;
        }
    }
}
