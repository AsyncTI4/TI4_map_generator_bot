package ti4.spring.service.roundstats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.game.GameUndoNameService;

@Service
@AllArgsConstructor
public class GameRoundStatsService {

    private static final String TACTICAL_OPEN_KEY_PREFIX = "roundstats_tactical_open_";
    private static final String TACTICAL_HAD_COMBAT_KEY_PREFIX = "roundstats_tactical_had_combat_";

    private final GameRoundPlayerStatsRepository canonicalRepository;
    private final GameRoundPlayerStatsSnapshotRepository snapshotRepository;

    @Transactional
    public void refreshOnSave(Game game, int createdUndoIndex) {
        if (game == null) return;

        int round = game.getRound();
        for (Player player : game.getRealPlayers()) {
            if (!isTrackable(game, player)) continue;
            GameRoundPlayerStats row = getOrCreate(game, player, round);
            double observedArmyCost = player.getTotalResourceValueOfUnits("both");
            row.setLastSeenArmyCost(observedArmyCost);
            if (row.getMaxArmyCost() == null || observedArmyCost > row.getMaxArmyCost()) {
                row.setMaxArmyCost(observedArmyCost);
            }
            row.setScPicks(formatScPicks(player));
            canonicalRepository.save(row);
        }

        if (createdUndoIndex > 0) {
            snapshotCanonical(game.getName(), createdUndoIndex);
        }
        pruneSnapshotsToExistingUndoFiles(game.getName());
    }

    @Transactional
    public void restoreAfterUndo(Game game, int targetUndoIndex) {
        if (game == null || targetUndoIndex <= 0) return;

        String gameId = game.getName();
        List<GameRoundPlayerStatsSnapshot> snapshotRows =
                snapshotRepository.findByGameIdAndUndoIndex(gameId, targetUndoIndex);

        if (!snapshotRows.isEmpty()) {
            canonicalRepository.deleteByGameId(gameId);
            canonicalRepository.saveAll(
                    snapshotRows.stream().map(this::toCanonical).toList());
        }

        snapshotRepository.deleteByGameIdAndUndoIndexGreaterThan(gameId, targetUndoIndex);
        canonicalRepository.deleteByGameIdAndRoundGreaterThan(gameId, game.getRound());

        for (Player player : game.getRealPlayers()) {
            clearTacticalMarkers(game, player);
        }
    }

    @Transactional
    public void incrementCombatsInitiated(Game game, Player player) {
        if (!isTrackable(game, player)) return;

        GameRoundPlayerStats row = getOrCreate(game, player, game.getRound());
        row.setCombatsInitiated(incrementNullable(row.getCombatsInitiated(), 1));
        canonicalRepository.save(row);

        if (isTacticalOpen(game, player)) {
            game.setStoredValue(tacticalHadCombatKey(player), Boolean.TRUE.toString());
        }
    }

    public void markTacticalStart(Game game, Player player) {
        if (!isTrackable(game, player)) return;
        game.setStoredValue(tacticalOpenKey(player), Integer.toString(game.getRound()));
        game.setStoredValue(tacticalHadCombatKey(player), Boolean.FALSE.toString());
    }

    @Transactional
    public void finalizeTactical(Game game, Player player) {
        if (!isTrackable(game, player)) {
            clearTacticalMarkers(game, player);
            return;
        }

        boolean tacticalHadCombat =
                isTacticalOpen(game, player) && Boolean.parseBoolean(game.getStoredValue(tacticalHadCombatKey(player)));
        if (tacticalHadCombat) {
            GameRoundPlayerStats row = getOrCreate(game, player, game.getRound());
            row.setTacticalsWithCombat(incrementNullable(row.getTacticalsWithCombat(), 1));
            canonicalRepository.save(row);
        }
        clearTacticalMarkers(game, player);
    }

    public void clearTacticalMarkers(Game game, Player player) {
        if (game == null || player == null) return;
        game.removeStoredValue(tacticalOpenKey(player));
        game.removeStoredValue(tacticalHadCombatKey(player));
    }

    @Transactional
    public void recordPlanetTaken(Game game, Player player, boolean stolen) {
        if (!isTrackable(game, player)) return;
        GameRoundPlayerStats row = getOrCreate(game, player, game.getRound());
        row.setPlanetsTaken(incrementNullable(row.getPlanetsTaken(), 1));
        if (stolen) {
            row.setPlanetsStolen(incrementNullable(row.getPlanetsStolen(), 1));
        }
        canonicalRepository.save(row);
    }

    @Transactional
    public void recordTechGained(Game game, Player player, String techId) {
        if (!isTrackable(game, player) || StringUtils.isBlank(techId)) return;
        GameRoundPlayerStats row = getOrCreate(game, player, game.getRound());
        row.setTechsGained(appendDistinctCsv(row.getTechsGained(), techId));
        canonicalRepository.save(row);
    }

    @Transactional
    public void recordDiceRolled(Game game, Player player, int diceCount) {
        if (!isTrackable(game, player) || diceCount <= 0) return;
        GameRoundPlayerStats row = getOrCreate(game, player, game.getRound());
        row.setDiceRolled(incrementNullable(row.getDiceRolled(), diceCount));
        canonicalRepository.save(row);
    }

    @Transactional
    public void recordTurnTime(Game game, Player player, long effectiveMs) {
        if (!isTrackable(game, player) || effectiveMs < 0) return;
        GameRoundPlayerStats row = getOrCreate(game, player, game.getRound());
        row.setTurnTimes(appendCsvLong(row.getTurnTimes(), effectiveMs));
        canonicalRepository.save(row);
    }

    private void snapshotCanonical(String gameId, int undoIndex) {
        snapshotRepository.deleteByGameIdAndUndoIndex(gameId, undoIndex);
        List<GameRoundPlayerStats> canonicalRows = canonicalRepository.findByGameId(gameId);
        if (canonicalRows.isEmpty()) return;
        List<GameRoundPlayerStatsSnapshot> snapshots =
                canonicalRows.stream().map(row -> toSnapshot(row, undoIndex)).toList();
        snapshotRepository.saveAll(snapshots);
    }

    private void pruneSnapshotsToExistingUndoFiles(String gameId) {
        List<Integer> undoIndexes = GameUndoNameService.getSortedUndoNumbers(gameId);
        if (undoIndexes.isEmpty()) return;
        snapshotRepository.deleteByGameIdAndUndoIndexNotIn(gameId, undoIndexes);
    }

    private boolean isTacticalOpen(Game game, Player player) {
        return Integer.toString(game.getRound()).equals(game.getStoredValue(tacticalOpenKey(player)));
    }

    private String tacticalOpenKey(Player player) {
        return TACTICAL_OPEN_KEY_PREFIX + player.getUserID();
    }

    private String tacticalHadCombatKey(Player player) {
        return TACTICAL_HAD_COMBAT_KEY_PREFIX + player.getUserID();
    }

    private boolean isTrackable(Game game, Player player) {
        return game != null
                && player != null
                && player.isRealPlayer()
                && StringUtils.isNotBlank(player.getUserID())
                && StringUtils.isNotBlank(game.getName());
    }

    private GameRoundPlayerStats getOrCreate(Game game, Player player, int round) {
        GameRoundPlayerStatsId id = new GameRoundPlayerStatsId(game.getName(), player.getUserID(), round);
        return canonicalRepository.findById(id).orElseGet(() -> {
            GameRoundPlayerStats row = new GameRoundPlayerStats();
            row.setGameId(game.getName());
            row.setUserDiscordId(player.getUserID());
            row.setRound(round);
            return row;
        });
    }

    private Integer incrementNullable(Integer value, int increment) {
        if (value == null) return increment;
        return value + increment;
    }

    private String appendDistinctCsv(String existingCsv, String newValue) {
        Set<String> values = new TreeSet<>();
        if (StringUtils.isNotBlank(existingCsv)) {
            Arrays.stream(existingCsv.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .forEach(values::add);
        }
        values.add(newValue.trim());
        return String.join(",", values);
    }

    private String appendCsvLong(String existingCsv, long valueToAppend) {
        List<String> values = new ArrayList<>();
        if (StringUtils.isNotBlank(existingCsv)) {
            values.addAll(Arrays.stream(existingCsv.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotBlank)
                    .toList());
        }
        values.add(Long.toString(valueToAppend));
        return String.join(",", values);
    }

    private String formatScPicks(Player player) {
        if (player.getSCs().isEmpty()) return null;
        return player.getSCs().stream()
                .sorted()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
    }

    private GameRoundPlayerStatsSnapshot toSnapshot(GameRoundPlayerStats canonical, int undoIndex) {
        GameRoundPlayerStatsSnapshot snapshot = new GameRoundPlayerStatsSnapshot();
        snapshot.setGameId(canonical.getGameId());
        snapshot.setUndoIndex(undoIndex);
        snapshot.setUserDiscordId(canonical.getUserDiscordId());
        snapshot.setRound(canonical.getRound());
        snapshot.setMaxArmyCost(canonical.getMaxArmyCost());
        snapshot.setLastSeenArmyCost(canonical.getLastSeenArmyCost());
        snapshot.setScPicks(canonical.getScPicks());
        snapshot.setCombatsInitiated(canonical.getCombatsInitiated());
        snapshot.setTacticalsWithCombat(canonical.getTacticalsWithCombat());
        snapshot.setPlanetsTaken(canonical.getPlanetsTaken());
        snapshot.setPlanetsStolen(canonical.getPlanetsStolen());
        snapshot.setTechsGained(canonical.getTechsGained());
        snapshot.setDiceRolled(canonical.getDiceRolled());
        snapshot.setTurnTimes(canonical.getTurnTimes());
        return snapshot;
    }

    private GameRoundPlayerStats toCanonical(GameRoundPlayerStatsSnapshot snapshot) {
        GameRoundPlayerStats canonical = new GameRoundPlayerStats();
        canonical.setGameId(snapshot.getGameId());
        canonical.setUserDiscordId(snapshot.getUserDiscordId());
        canonical.setRound(snapshot.getRound());
        canonical.setMaxArmyCost(snapshot.getMaxArmyCost());
        canonical.setLastSeenArmyCost(snapshot.getLastSeenArmyCost());
        canonical.setScPicks(snapshot.getScPicks());
        canonical.setCombatsInitiated(snapshot.getCombatsInitiated());
        canonical.setTacticalsWithCombat(snapshot.getTacticalsWithCombat());
        canonical.setPlanetsTaken(snapshot.getPlanetsTaken());
        canonical.setPlanetsStolen(snapshot.getPlanetsStolen());
        canonical.setTechsGained(snapshot.getTechsGained());
        canonical.setDiceRolled(snapshot.getDiceRolled());
        canonical.setTurnTimes(snapshot.getTurnTimes());
        return canonical;
    }
}
