package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ThirtyKExploreMathTest {

    // Corrosive Discovery ("above 4" = 5+, "below 3" = 1-2), Systematic Sympathies (4 resources of ships,
    // fighters 0.5), Wasteful Pirates (only assignable hits pay), Cross-species Mingling (X + 1).
    @Test
    void cardNumbersFollowTheCardText() {
        List<Integer> rolls = List.of(1, 2, 3, 4, 5, 10);
        assertThat(ThirtyKExploreMath.countAtLeast(rolls, 5)).isEqualTo(2);
        assertThat(ThirtyKExploreMath.countAtMost(rolls, 2)).isEqualTo(2);
        assertThat(ThirtyKExploreMath.sympathiesEarnsVictoryPoint(3.5)).isFalse();
        assertThat(ThirtyKExploreMath.sympathiesEarnsVictoryPoint(4.0)).isTrue();
        assertThat(ThirtyKExploreMath.wastefulPiratesTradeGoods(4, 2)).isEqualTo(2);
        assertThat(ThirtyKExploreMath.crossSpeciesInfantry(0)).isEqualTo(1);
    }

    @Test
    void itsATrapPicksFewestPointsAndBreaksTiesAmongThemOnly() {
        Map<String, Integer> points = new LinkedHashMap<>();
        points.put("a", 5);
        points.put("b", 2);
        points.put("c", 2);
        Random random = new Random(7);
        for (int i = 0; i < 20; i++) {
            assertThat(ThirtyKExploreMath.pickFewestPoints(points, random))
                    .get()
                    .isIn("b", "c");
        }
        assertThat(ThirtyKExploreMath.pickFewestPoints(Map.of(), random)).isEmpty();
    }

    // Cards ready when status homework starts, so status scoring still belongs to the action-phase cycle
    // (Profitable Factories must not pay twice for one ready cycle).
    @Test
    void attachmentsReadyAgainOnlyOnceStatusHomeworkStarts() {
        String actionPhase = ThirtyKExploreMath.readyCycleMarker(3, "action");
        String agendaPhase = ThirtyKExploreMath.readyCycleMarker(3, "agendaVoting");
        assertThat(ThirtyKExploreMath.readyCycleMarker(3, "statusScoring")).isEqualTo(actionPhase);
        assertThat(ThirtyKExploreMath.readyCycleMarker(3, "statusHomework"))
                .isEqualTo(agendaPhase)
                .isNotEqualTo(actionPhase);
        assertThat(ThirtyKExploreMath.readyCycleMarker(4, "strategy")).isNotEqualTo(actionPhase);
    }
}
