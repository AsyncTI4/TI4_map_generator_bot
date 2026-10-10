package ti4.game.manager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.model.BorderAnomalyHolder;
import ti4.testUtils.BaseTi4Test;

class BorderAnomalyManagerTest extends BaseTi4Test {

    @Test
    void setNormalisesLegacyIdsDropsArrowsAndKeepsUnknownTypes() {
        BorderAnomalyManager manager = new BorderAnomalyManager();

        manager.set(List.of(
                new BorderAnomalyHolder("101", 0, "VOID_TETHER"),
                new BorderAnomalyHolder("102", 1, "ARROW"),
                new BorderAnomalyHolder("103", 2, "FOO")));

        assertThat(manager.get())
                .extracting(BorderAnomalyHolder::getTile, BorderAnomalyHolder::getType)
                .containsExactly(tuple("101", "void_tether"), tuple("103", "FOO"));
    }

    @Test
    void unknownTypeBlocksNothing() {
        BorderAnomalyManager manager = new BorderAnomalyManager();
        manager.add("101", 0, "FOO");

        BorderAnomalyHolder holder = manager.get().getFirst();
        assertThat(holder.getModel()).isNull();
        assertThat(holder.blocksAdjacencyIn()).isFalse();
        assertThat(holder.blocksAdjacencyOut()).isFalse();
    }

    @Test
    void addNormalisesDisplayNames() {
        BorderAnomalyManager manager = new BorderAnomalyManager();
        manager.add("101", 0, "Spatial Tear");

        assertThat(manager.get().getFirst().getType()).isEqualTo("spatial_tear");
        assertThat(manager.has("101", 0)).isTrue();
    }

    @Test
    void setWithItsOwnListKeepsTheAnomalies() {
        BorderAnomalyManager manager = new BorderAnomalyManager();
        manager.add("101", 0, "nebula");

        manager.set(manager.get());

        assertThat(manager.get()).hasSize(1);
    }
}
