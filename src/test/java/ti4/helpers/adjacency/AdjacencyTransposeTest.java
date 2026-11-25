package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.TestGameHarness;
import ti4.testUtils.BaseTi4Test;

// into() must be the exact reverse of from(): b is in from(a) exactly when a is in into(b). This is what proves
// every rule's inbound side, including the one-way ones (borders, custom links, ingress tokens, Ghoti, ...).
class AdjacencyTransposeTest extends BaseTi4Test {

    @ParameterizedTest
    @EnumSource(AdjacencyScenarios.class)
    void intoIsTheReverseOfFrom(AdjacencyScenarios scenario) {
        try (var harness = TestGameHarness.fromSourceGame(scenario.fixture)) {
            Game game = harness.load();
            scenario.apply(game);

            List<String> mismatches = new ArrayList<>();
            for (Player player : AdjacencyScenarios.playersIncludingNone(game)) {
                for (AdjacencyPurpose purpose : AdjacencyPurpose.values()) {
                    Adjacency adjacency = Adjacency.of(game, player).forPurpose(purpose);
                    Set<String> positions = new TreeSet<>(AdjacencyScenarios.positions(game));
                    for (String position : AdjacencyScenarios.positions(game)) {
                        positions.addAll(adjacency.from(position));
                    }
                    for (String target : positions) {
                        Set<String> expected = new TreeSet<>();
                        for (String source : positions) {
                            if (adjacency.from(source).contains(target)) expected.add(source);
                        }
                        Set<String> actual = new TreeSet<>(adjacency.into(target));
                        if (!expected.equals(actual)) {
                            mismatches.add("%s %s into(%s) expected=%s actual=%s"
                                    .formatted(
                                            player == null ? "null" : player.getFaction(),
                                            purpose,
                                            target,
                                            expected,
                                            actual));
                        }
                    }
                }
            }
            assertThat(mismatches).as(scenario.name()).isEmpty();
        }
    }
}
