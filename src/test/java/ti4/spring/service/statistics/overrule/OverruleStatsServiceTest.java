package ti4.spring.service.statistics.overrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ti4.game.GameStats.OverruleTargetMigration.OverruleEntry;

class OverruleStatsServiceTest {

    @Test
    void recordPlayPersistsOneRowPerCall() {
        OverrulePlayRepository repository = mock(OverrulePlayRepository.class);
        OverruleStatsService service = new OverruleStatsService(repository);

        service.recordPlay("pbd0001", "user-1", "Warfare");

        ArgumentCaptor<OverrulePlayEntity> captor = ArgumentCaptor.forClass(OverrulePlayEntity.class);
        verify(repository).save(captor.capture());
        OverrulePlayEntity saved = captor.getValue();
        assertThat(saved.getGameName()).isEqualTo("pbd0001");
        assertThat(saved.getPlayerId()).isEqualTo("user-1");
        assertThat(saved.getStrategyCard()).isEqualTo("Warfare");
    }

    @Test
    void addMigratedPlaysBulkInsertsWithGameNameFilledIn() {
        OverrulePlayRepository repository = mock(OverrulePlayRepository.class);
        OverruleStatsService service = new OverruleStatsService(repository);

        service.addMigratedPlays(
                "pbd0002", List.of(new OverruleEntry("user-a", "Politics"), new OverruleEntry("user-b", "Trade")));

        ArgumentCaptor<List<OverrulePlayEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(
                        OverrulePlayEntity::getGameName,
                        OverrulePlayEntity::getPlayerId,
                        OverrulePlayEntity::getStrategyCard)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("pbd0002", "user-a", "Politics"),
                        org.assertj.core.groups.Tuple.tuple("pbd0002", "user-b", "Trade"));
    }

    @Test
    void findPlaysForGamesFiltersOnGameName() {
        OverrulePlayRepository repository = mock(OverrulePlayRepository.class);
        when(repository.findAll())
                .thenReturn(List.of(
                        new OverrulePlayEntity("keep", "user-1", "Warfare"),
                        new OverrulePlayEntity("drop", "user-2", "Politics"),
                        new OverrulePlayEntity("keep", "user-3", "Trade")));

        List<OverrulePlayEntity> plays =
                new OverruleStatsService(repository).findPlaysForGames(java.util.Set.of("keep"));

        assertThat(plays).extracting(OverrulePlayEntity::getGameName).containsExactly("keep", "keep");
    }
}
