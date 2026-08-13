package ti4.spring.service.statistics.overrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OverruleChoiceMigrationRunnerTest {

    @Test
    void fansEachCountRowOutIntoThatManyPerPlayEntitiesWithNullPlayerId() {
        OverruleChoiceRepository legacy = mock(OverruleChoiceRepository.class);
        OverrulePlayRepository plays = mock(OverrulePlayRepository.class);
        when(legacy.findAll()).thenReturn(List.of(choice("g1", "Warfare", 3), choice("g2", "Politics", 1)));

        int migrated = OverruleChoiceMigrationRunner.fanOutLegacyRowsIntoPlays(legacy, plays);

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
    void doesNothingWhenTheLegacyTableIsEmpty() {
        OverruleChoiceRepository legacy = mock(OverruleChoiceRepository.class);
        OverrulePlayRepository plays = mock(OverrulePlayRepository.class);
        when(legacy.findAll()).thenReturn(List.of());

        int migrated = OverruleChoiceMigrationRunner.fanOutLegacyRowsIntoPlays(legacy, plays);

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

    @SuppressWarnings("unchecked")
    private static <T> Iterable<T> anyIterable() {
        return (Iterable<T>) org.mockito.ArgumentMatchers.any(Iterable.class);
    }
}
