package ti4.spring.service.persistence;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record PersistedGameState(GameColumns game, List<PlayerRow> players, List<TitleRow> titles) {

    public record GameColumns(
            String gameName,
            long creationEpochMilliseconds,
            Long endedEpochMilliseconds,
            int round,
            int victoryPointGoal,
            boolean completed,
            boolean prophecyOfKings,
            boolean thundersEdge,
            boolean twilightsFall,
            boolean fractureInPlay,
            boolean homebrew,
            boolean discordantStarsMode,
            boolean absolMode,
            boolean frankenMode,
            boolean allianceMode,
            boolean twilightImperiumGlobalLeague,
            boolean twilightImperiumGlobalLeagueFractured,
            String twilightImperiumGlobalLeagueRank,
            int playerCount) {}

    public record PlayerRow(
            String userId,
            String factionName,
            int score,
            int totalNumberOfTurns,
            long totalTurnTime,
            double expectedHits,
            int actualHits,
            boolean eliminated,
            boolean winner,
            boolean replaced) {}

    public record TitleRow(String userId, String title) {}

    public static PersistedGameState of(GameEntitySnapshot snapshot) {
        return of(snapshot.game(), snapshot.game().getPlayers(), snapshot.titles());
    }

    static PersistedGameState of(GameEntity game, Collection<PlayerEntity> players, Collection<TitleEntity> titles) {
        List<PlayerRow> playerRows = players.stream()
                .map(PersistedGameState::toPlayerRow)
                .sorted(Comparator.comparing(PlayerRow::toString))
                .toList();
        List<TitleRow> titleRows = titles.stream()
                .map(title -> new TitleRow(title.getUser().getId(), title.getTitle()))
                .sorted(Comparator.comparing(TitleRow::toString))
                .toList();
        return new PersistedGameState(toGameColumns(game), playerRows, titleRows);
    }

    public List<String> describeDifferencesFrom(PersistedGameState actual) {
        List<String> differences = new ArrayList<>();
        List<String> differingColumns = differingComponents(game, actual.game);
        if (!differingColumns.isEmpty()) {
            differences.add("game columns " + String.join(", ", differingColumns));
        }
        if (!players.equals(actual.players)) {
            differences.add(describeRowDifference("player", players, actual.players));
        }
        if (!titles.equals(actual.titles)) {
            differences.add(describeRowDifference("title", titles, actual.titles));
        }
        return differences;
    }

    private static String describeRowDifference(String rowName, List<?> expected, List<?> actual) {
        long missing = expected.stream().filter(row -> !actual.contains(row)).count();
        long unexpected = actual.stream().filter(row -> !expected.contains(row)).count();
        return String.format("%s rows (%d missing, %d unexpected)", rowName, missing, unexpected);
    }

    private static List<String> differingComponents(Record expected, Record actual) {
        List<String> differing = new ArrayList<>();
        for (RecordComponent component : expected.getClass().getRecordComponents()) {
            Object expectedValue = componentValue(component, expected);
            Object actualValue = componentValue(component, actual);
            if (!Objects.equals(expectedValue, actualValue)) {
                differing.add(component.getName() + " (expected " + expectedValue + ", found " + actualValue + ")");
            }
        }
        return differing;
    }

    private static Object componentValue(RecordComponent component, Record record) {
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read record component " + component.getName(), e);
        }
    }

    private static GameColumns toGameColumns(GameEntity game) {
        return new GameColumns(
                game.getGameName(),
                game.getCreationEpochMilliseconds(),
                game.getEndedEpochMilliseconds(),
                game.getRound(),
                game.getVictoryPointGoal(),
                game.isCompleted(),
                game.isProphecyOfKings(),
                game.isThundersEdge(),
                game.isTwilightsFall(),
                game.isFractureInPlay(),
                game.isHomebrew(),
                game.isDiscordantStarsMode(),
                game.isAbsolMode(),
                game.isFrankenMode(),
                game.isAllianceMode(),
                game.isTwilightImperiumGlobalLeague(),
                game.isTwilightImperiumGlobalLeagueFractured(),
                game.getTwilightImperiumGlobalLeagueRank(),
                game.getPlayerCount());
    }

    private static PlayerRow toPlayerRow(PlayerEntity player) {
        return new PlayerRow(
                player.getUser().getId(),
                player.getFactionName(),
                player.getScore(),
                player.getTotalNumberOfTurns(),
                player.getTotalTurnTime(),
                player.getExpectedHits(),
                player.getActualHits(),
                player.isEliminated(),
                player.isWinner(),
                player.isReplaced());
    }
}
