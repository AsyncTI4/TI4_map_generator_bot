package ti4.spring.service.statistics;

import java.util.Collection;
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
public class AverageHitsPerTurnService {

    private static final int DEFAULT_PLAYER_LIMIT = 50;
    private static final int DEFAULT_MINIMUM_TURNS = 100;
    private static final int ANOMALOUS_NUMBER_OF_HITS_THRESHOLD = 200;

    private final PlayerEntityRepository playerEntityRepository;

    @Transactional(readOnly = true)
    public void getExpectedHitsPerTurn(SlashCommandInteractionEvent event) {
        boolean sortOrderAscending = event.getOption("ascending", true, OptionMapping::getAsBoolean);
        int topLimit = event.getOption(Constants.TOP_LIMIT, DEFAULT_PLAYER_LIMIT, OptionMapping::getAsInt);
        int minimumTurns =
                event.getOption(Constants.MINIMUM_NUMBER_OF_TURNS, DEFAULT_MINIMUM_TURNS, OptionMapping::getAsInt);

        List<PlayerEntity> players = playerEntityRepository.findAllWithUsersByCompletedGame();

        Map<UserEntity, HitsPerTurnAccumulator> usersToAccumulators = getUsersToHitsPerTurnAccumulators(players);

        List<HitsPerTurnAccumulator> filteredAccumulators = usersToAccumulators.values().stream()
                .filter(accumulator -> accumulator.totalNumberOfTurns >= minimumTurns)
                .toList();

        Map<HitsPerTurnAccumulator, String> tiers = getTiers(usersToAccumulators.values());
        List<HitsPerTurnAccumulator> sortedResults = filteredAccumulators.stream()
                .sorted((a, b) -> sortOrderAscending
                        ? Double.compare(a.getAverageExpectedHitsPerTurn(), b.getAverageExpectedHitsPerTurn())
                        : Double.compare(b.getAverageExpectedHitsPerTurn(), a.getAverageExpectedHitsPerTurn()))
                .limit(topLimit)
                .toList();

        String result = toResultString(sortedResults, tiers) + getOverallAverageString(usersToAccumulators.values());

        MessageHelper.sendMessageToThread(event.getChannel(), "Average Expected Hits Per Turn", result);
    }

    private static String getOverallAverageString(Collection<HitsPerTurnAccumulator> accumulators) {
        List<HitsPerTurnAccumulator> accumulatorsForOverallAverage = accumulators.stream()
                // for percentiles, we want to filter out low turn users
                .filter(accumulator -> accumulator.totalNumberOfTurns >= DEFAULT_MINIMUM_TURNS)
                .toList();
        double totalExpectedHits = accumulatorsForOverallAverage.stream()
                .mapToDouble(accumulator -> accumulator.totalExpectedHits)
                .sum();
        int totalNumberOfTurns = accumulatorsForOverallAverage.stream()
                .mapToInt(accumulator -> accumulator.totalNumberOfTurns)
                .sum();
        double overallAverage = totalNumberOfTurns == 0 ? 0 : totalExpectedHits / totalNumberOfTurns;

        return String.format("\nThe overall average is %.1f", overallAverage);
    }

    @NotNull
    private static Map<UserEntity, HitsPerTurnAccumulator> getUsersToHitsPerTurnAccumulators(
            Iterable<PlayerEntity> players) {
        Map<UserEntity, HitsPerTurnAccumulator> userToAccumulators = new HashMap<>();
        for (PlayerEntity player : players) {
            if (player.getTotalNumberOfTurns() == 0) continue;
            // Ignore anomaly games (infinite combats, spamming buttons, Franken/TF madness)
            if (player.getExpectedHits() >= ANOMALOUS_NUMBER_OF_HITS_THRESHOLD) continue;
            userToAccumulators
                    .computeIfAbsent(player.getUser(), user -> new HitsPerTurnAccumulator(user.getName()))
                    .addGame(player.getExpectedHits(), player.getTotalNumberOfTurns());
        }
        return userToAccumulators;
    }

    @Transactional(readOnly = true)
    public String getAverageHitsPerTurn(List<String> userIds) {
        List<PlayerEntity> players = playerEntityRepository.findAllWithUsersByUserIdIn(userIds);

        Map<UserEntity, HitsPerTurnAccumulator> usersToAccumulators = getUsersToHitsPerTurnAccumulators(players);
        Collection<HitsPerTurnAccumulator> accumulators = usersToAccumulators.values();

        List<HitsPerTurnAccumulator> sortedResults = accumulators.stream()
                .sorted((a, b) -> Double.compare(b.getAverageExpectedHitsPerTurn(), a.getAverageExpectedHitsPerTurn()))
                .toList();

        return toResultString(sortedResults, null);
    }

    private String toResultString(
            Collection<HitsPerTurnAccumulator> accumulators, Map<HitsPerTurnAccumulator, String> tiers) {
        StringBuilder sb = new StringBuilder("## __**Average Expected Hits Per Turn**__\n");
        int index = 1;
        for (var accumulator : accumulators) {
            sb.append("`")
                    .append(Helper.leftpad(String.valueOf(index), 3))
                    .append(". ")
                    .append(String.format("%.2f", accumulator.getAverageExpectedHitsPerTurn()))
                    .append("` ")
                    .append(accumulator.username)
                    .append(" [")
                    .append(String.format("%.1f", accumulator.totalExpectedHits))
                    .append(" expected hits / ")
                    .append(accumulator.totalNumberOfTurns)
                    .append(" total turns]");
            if (tiers != null && tiers.containsKey(accumulator)) {
                sb.append(" [").append(tiers.get(accumulator)).append("]");
            }
            sb.append("\n");
            index++;
        }
        return sb.toString();
    }

    private static Map<HitsPerTurnAccumulator, String> getTiers(Collection<HitsPerTurnAccumulator> accumulators) {
        List<HitsPerTurnAccumulator> ascendingResults = accumulators.stream()
                // for percentiles, we want to filter out low turn users
                .filter(accumulator -> accumulator.totalNumberOfTurns >= DEFAULT_MINIMUM_TURNS)
                .sorted(Comparator.comparingDouble(HitsPerTurnAccumulator::getAverageExpectedHitsPerTurn))
                .toList();
        int totalPlayers = ascendingResults.size();
        return IntStream.range(0, totalPlayers)
                .boxed()
                .collect(Collectors.toMap(ascendingResults::get, i -> getPercentile(i + 1, totalPlayers)));
    }

    private static String getPercentile(int index, int totalPlayers) {
        double percentile = ((double) index / totalPlayers) * 100;
        return String.format("%.2f%% have lower", percentile);
    }

    public static AverageHitsPerTurnService getBean() {
        return SpringContext.getBean(AverageHitsPerTurnService.class);
    }

    private static class HitsPerTurnAccumulator {
        private final String username;
        private double totalExpectedHits;
        private int totalNumberOfTurns;

        HitsPerTurnAccumulator(String username) {
            this.username = username;
        }

        void addGame(double expectedHits, int turns) {
            totalExpectedHits += expectedHits;
            totalNumberOfTurns += turns;
        }

        double getAverageExpectedHitsPerTurn() {
            return totalNumberOfTurns == 0 ? 0 : totalExpectedHits / totalNumberOfTurns;
        }
    }
}
