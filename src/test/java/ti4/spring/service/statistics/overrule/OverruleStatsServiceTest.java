package ti4.spring.service.statistics.overrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("deprecation")
class OverruleStatsServiceTest {

    @Test
    void recordPlayPersistsOneRowPerCall() {
        OverrulePlayRepository plays = mock(OverrulePlayRepository.class);
        OverruleChoiceRepository legacy = mock(OverruleChoiceRepository.class);
        OverruleStatsService service = new OverruleStatsService(plays, legacy);

        service.recordPlay("pbd0001", "user-1", "Warfare");

        ArgumentCaptor<OverrulePlayEntity> captor = ArgumentCaptor.forClass(OverrulePlayEntity.class);
        verify(plays).save(captor.capture());
        OverrulePlayEntity saved = captor.getValue();
        assertThat(saved.getGameName()).isEqualTo("pbd0001");
        assertThat(saved.getPlayerId()).isEqualTo("user-1");
        assertThat(saved.getStrategyCard()).isEqualTo("Warfare");
    }

    @Test
    void findPlaysForGamesFiltersOnGameName() {
        OverrulePlayRepository plays = mock(OverrulePlayRepository.class);
        OverruleChoiceRepository legacy = mock(OverruleChoiceRepository.class);
        when(plays.findAll())
                .thenReturn(List.of(
                        new OverrulePlayEntity("keep", "user-1", "Warfare"),
                        new OverrulePlayEntity("drop", "user-2", "Politics"),
                        new OverrulePlayEntity("keep", "user-3", "Trade")));

        List<OverrulePlayEntity> filtered =
                new OverruleStatsService(plays, legacy).findPlaysForGames(java.util.Set.of("keep"));

        assertThat(filtered).extracting(OverrulePlayEntity::getGameName).containsExactly("keep", "keep");
    }

    @Test
    void migrateChoicesToPlaysFansOutAndDeletesLegacyRows() {
        OverrulePlayRepository plays = mock(OverrulePlayRepository.class);
        OverruleChoiceRepository legacy = mock(OverruleChoiceRepository.class);
        when(legacy.findAll()).thenReturn(List.of(choice("g1", "Warfare", 3), choice("g2", "Politics", 1)));

        int migrated = new OverruleStatsService(plays, legacy).migrateChoicesToPlays();

        assertThat(migrated).isEqualTo(4);
        ArgumentCaptor<List<OverrulePlayEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(plays).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(
                        OverrulePlayEntity::getGameName,
                        OverrulePlayEntity::getPlayerId,
                        OverrulePlayEntity::getStrategyCard)
                .containsExactly(
                        tuple("g1", null, "Warfare"),
                        tuple("g1", null, "Warfare"),
                        tuple("g1", null, "Warfare"),
                        tuple("g2", null, "Politics"));
        verify(legacy).deleteAll(anyIterable());
    }

    @Test
    void migrateChoicesToPlaysDoesNothingWhenTheLegacyTableIsEmpty() {
        OverrulePlayRepository plays = mock(OverrulePlayRepository.class);
        OverruleChoiceRepository legacy = mock(OverruleChoiceRepository.class);
        when(legacy.findAll()).thenReturn(List.of());

        int migrated = new OverruleStatsService(plays, legacy).migrateChoicesToPlays();

        assertThat(migrated).isZero();
        verifyNoInteractions(plays);
    }

    private static OverruleChoiceEntity choice(String gameName, String strategyCard, long count) {
        OverruleChoiceEntity row = new OverruleChoiceEntity();
        row.setGameName(gameName);
        row.setStrategyCard(strategyCard);
        row.setCount(count);
        return row;
    }
}
