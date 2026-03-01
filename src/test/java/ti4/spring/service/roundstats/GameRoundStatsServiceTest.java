package ti4.spring.service.roundstats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import ti4.map.Game;
import ti4.map.Player;

class GameRoundStatsServiceTest {

    private static final String GAME_NAME = "test-game";

    private GameRoundPlayerStatsRepository canonicalRepository;
    private GameRoundPlayerStatsSnapshotRepository snapshotRepository;
    private GameRoundStatsService service;

    private final Map<GameRoundPlayerStatsId, GameRoundPlayerStats> canonicalStore = new HashMap<>();

    @BeforeEach
    void beforeEach() {
        canonicalRepository = Mockito.mock(GameRoundPlayerStatsRepository.class);
        snapshotRepository = Mockito.mock(GameRoundPlayerStatsSnapshotRepository.class);
        service = new GameRoundStatsService(canonicalRepository, snapshotRepository);

        when(canonicalRepository.findById(any())).thenAnswer(invocation -> {
            GameRoundPlayerStatsId id = invocation.getArgument(0);
            return java.util.Optional.ofNullable(canonicalStore.get(id));
        });
        when(canonicalRepository.save(any())).thenAnswer(invocation -> {
            GameRoundPlayerStats row = invocation.getArgument(0);
            GameRoundPlayerStatsId id =
                    new GameRoundPlayerStatsId(row.getGameId(), row.getUserDiscordId(), row.getRound());
            canonicalStore.put(id, row);
            return row;
        });
    }

    @Test
    void refreshOnSaveUsesCreatedUndoIndexForSnapshots() {
        Game game = newGameWithPlayer();
        Player player = game.getRealPlayers().getFirst();
        player.addSC(5);
        player.addSC(3);

        GameRoundPlayerStats canonical = new GameRoundPlayerStats();
        canonical.setGameId(GAME_NAME);
        canonical.setUserDiscordId(player.getUserID());
        canonical.setRound(game.getRound());
        canonical.setScPicks("3,5");
        when(canonicalRepository.findByGameId(GAME_NAME)).thenReturn(List.of(canonical));

        int createdUndoIndex = 7;
        service.refreshOnSave(game, createdUndoIndex);

        verify(snapshotRepository).deleteByGameIdAndUndoIndex(GAME_NAME, createdUndoIndex);
        ArgumentCaptor<List<GameRoundPlayerStatsSnapshot>> snapshotCaptor = ArgumentCaptor.forClass(List.class);
        verify(snapshotRepository).saveAll(snapshotCaptor.capture());
        assertThat(snapshotCaptor.getValue()).hasSize(1);
        assertThat(snapshotCaptor.getValue().getFirst().getUndoIndex()).isEqualTo(createdUndoIndex);
        verify(canonicalRepository, times(1)).save(any(GameRoundPlayerStats.class));
    }

    @Test
    void restoreAfterUndoRestoresCanonicalAndClearsMarkers() {
        Game game = newGameWithPlayer();
        Player player = game.getRealPlayers().getFirst();
        game.setRound(2);
        game.setStoredValue("roundstats_tactical_open_" + player.getUserID(), "2");
        game.setStoredValue("roundstats_tactical_had_combat_" + player.getUserID(), "true");

        GameRoundPlayerStatsSnapshot snapshot = new GameRoundPlayerStatsSnapshot();
        snapshot.setGameId(GAME_NAME);
        snapshot.setUndoIndex(4);
        snapshot.setUserDiscordId(player.getUserID());
        snapshot.setRound(2);
        snapshot.setCombatsInitiated(3);
        snapshot.setTurnTimes("100,200");
        when(snapshotRepository.findByGameIdAndUndoIndex(GAME_NAME, 4)).thenReturn(List.of(snapshot));

        service.restoreAfterUndo(game, 4);

        verify(canonicalRepository).deleteByGameId(GAME_NAME);
        verify(canonicalRepository).saveAll(any(List.class));
        verify(snapshotRepository).deleteByGameIdAndUndoIndexGreaterThan(GAME_NAME, 4);
        verify(canonicalRepository).deleteByGameIdAndRoundGreaterThan(GAME_NAME, game.getRound());
        assertThat(game.getStoredValue("roundstats_tactical_open_" + player.getUserID()))
                .isEmpty();
        assertThat(game.getStoredValue("roundstats_tactical_had_combat_" + player.getUserID()))
                .isEmpty();
    }

    @Test
    void restoreAfterUndoDoesNotDeleteCanonicalWhenSnapshotMissing() {
        Game game = newGameWithPlayer();
        game.setRound(2);
        when(snapshotRepository.findByGameIdAndUndoIndex(GAME_NAME, 4)).thenReturn(List.of());

        service.restoreAfterUndo(game, 4);

        verify(canonicalRepository, never()).deleteByGameId(GAME_NAME);
        verify(canonicalRepository, never()).saveAll(any(List.class));
        verify(snapshotRepository).deleteByGameIdAndUndoIndexGreaterThan(GAME_NAME, 4);
        verify(canonicalRepository).deleteByGameIdAndRoundGreaterThan(GAME_NAME, game.getRound());
    }

    @Test
    void finalizeTacticalIncrementsOnceWhenCombatHappened() {
        Game game = newGameWithPlayer();
        Player player = game.getRealPlayers().getFirst();
        game.setRound(3);
        game.setStoredValue("roundstats_tactical_open_" + player.getUserID(), "3");
        game.setStoredValue("roundstats_tactical_had_combat_" + player.getUserID(), "true");

        service.finalizeTactical(game, player);
        service.finalizeTactical(game, player);

        GameRoundPlayerStatsId id = new GameRoundPlayerStatsId(GAME_NAME, player.getUserID(), 3);
        assertThat(canonicalStore.get(id).getTacticalsWithCombat()).isEqualTo(1);
        verify(canonicalRepository, times(1)).save(any(GameRoundPlayerStats.class));
    }

    @Test
    void tracksTechDiceAndTurnTimesWithExpectedAggregation() {
        Game game = newGameWithPlayer();
        Player player = game.getRealPlayers().getFirst();
        game.setRound(4);

        service.recordTechGained(game, player, "neural");
        service.recordTechGained(game, player, "neural");
        service.recordTechGained(game, player, "sarween");
        service.recordDiceRolled(game, player, 3);
        service.recordDiceRolled(game, player, 2);
        service.recordTurnTime(game, player, 1000);
        service.recordTurnTime(game, player, 2000);

        GameRoundPlayerStatsId id = new GameRoundPlayerStatsId(GAME_NAME, player.getUserID(), 4);
        GameRoundPlayerStats row = canonicalStore.get(id);
        assertThat(row.getTechsGained()).isEqualTo("neural,sarween");
        assertThat(row.getDiceRolled()).isEqualTo(5);
        assertThat(row.getTurnTimes()).isEqualTo("1000,2000");
    }

    @Test
    void incrementCombatsInitiatedMarksTacticalCombatWhenOpen() {
        Game game = newGameWithPlayer();
        Player player = game.getRealPlayers().getFirst();
        game.setRound(5);
        game.setStoredValue("roundstats_tactical_open_" + player.getUserID(), "5");

        service.incrementCombatsInitiated(game, player);

        GameRoundPlayerStatsId id = new GameRoundPlayerStatsId(GAME_NAME, player.getUserID(), 5);
        assertThat(canonicalStore.get(id).getCombatsInitiated()).isEqualTo(1);
        assertThat(game.getStoredValue("roundstats_tactical_had_combat_" + player.getUserID()))
                .isEqualTo("true");
    }

    @Test
    void finalizeTacticalDoesNotIncrementWhenNoCombat() {
        Game game = newGameWithPlayer();
        Player player = game.getRealPlayers().getFirst();
        game.setRound(6);
        game.setStoredValue("roundstats_tactical_open_" + player.getUserID(), "6");
        game.setStoredValue("roundstats_tactical_had_combat_" + player.getUserID(), "false");

        service.finalizeTactical(game, player);

        verify(canonicalRepository, never()).save(any(GameRoundPlayerStats.class));
    }

    @Test
    void refreshOnSaveMaintainsMonotonicMaxArmyCost() {
        Game game = newGameWithPlayer();
        Player player = game.getRealPlayers().getFirst();
        game.setRound(7);

        GameRoundPlayerStats existing = new GameRoundPlayerStats();
        existing.setGameId(GAME_NAME);
        existing.setUserDiscordId(player.getUserID());
        existing.setRound(7);
        existing.setMaxArmyCost(1000.0);
        existing.setLastSeenArmyCost(1000.0);
        canonicalStore.put(new GameRoundPlayerStatsId(GAME_NAME, player.getUserID(), 7), existing);
        when(canonicalRepository.findByGameId(GAME_NAME)).thenReturn(List.of(existing));

        service.refreshOnSave(game, 0);
        GameRoundPlayerStats row = canonicalStore.get(new GameRoundPlayerStatsId(GAME_NAME, player.getUserID(), 7));
        assertThat(row.getMaxArmyCost()).isEqualTo(1000.0);
        assertThat(row.getLastSeenArmyCost()).isNotNull();
    }

    private Game newGameWithPlayer() {
        Game game = new Game();
        game.setName(GAME_NAME);
        game.setRound(1);
        Player player = game.addPlayer("user-1", "user-1");
        player.setColor("red");
        player.setFaction("arborec");
        return game;
    }
}
