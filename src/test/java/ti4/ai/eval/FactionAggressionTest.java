package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class FactionAggressionTest extends BaseTi4Test {

    // A misspelt faction id would silently fall back to AVERAGE, so every listed id must name a real faction.
    @Test
    void everyListedFactionIdResolvesToAFaction() {
        for (FactionAggression level : FactionAggression.values()) {
            for (String id : level.factions()) {
                assertThat(Mapper.getFaction(id)).as(id).isNotNull();
            }
        }
    }

    @Test
    void noFactionIsListedUnderTwoLevels() {
        Set<String> seen = new HashSet<>();
        for (FactionAggression level : FactionAggression.values()) {
            for (String id : level.factions()) assertThat(seen.add(id)).as(id).isTrue();
        }
    }

    @Test
    void rankedByWillingnessToAttack() {
        AiTestGame test = new AiTestGame();

        assertThat(FactionAggression.of(test.nekro)).isEqualTo(FactionAggression.AGGRESSIVE);
        assertThat(FactionAggression.of(test.sol)).isEqualTo(FactionAggression.AVERAGE);
        assertThat(FactionAggression.of(test.addSeat("200000000000000077", "hacan", "red")))
                .isEqualTo(FactionAggression.PASSIVE);
        assertThat(FactionAggression.AGGRESSIVE.factor())
                .isGreaterThan(FactionAggression.AVERAGE.factor())
                .isGreaterThan(FactionAggression.PASSIVE.factor());
    }
}
