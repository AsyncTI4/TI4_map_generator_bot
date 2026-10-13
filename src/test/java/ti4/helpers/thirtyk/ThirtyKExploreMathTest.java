package ti4.helpers.thirtyk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import ti4.image.Mapper;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;
import ti4.model.Source.ComponentSource;
import ti4.testUtils.BaseTi4Test;

class ThirtyKExploreMathTest extends BaseTi4Test {

    @Test
    void countsDiceAboveAndBelowThresholds() {
        // Corrosive Discovery: "above 4" is 5+, "below 3" is 1-2.
        List<Integer> rolls = List.of(1, 2, 3, 4, 5, 10);
        assertThat(ThirtyKExploreMath.countAtLeast(rolls, 5)).isEqualTo(2);
        assertThat(ThirtyKExploreMath.countAtMost(rolls, 2)).isEqualTo(2);
    }

    @Test
    void systematicSympathiesNeedsFourResourcesOfShips() {
        assertThat(ThirtyKExploreMath.sympathiesEarnsVictoryPoint(3.5)).isFalse();
        // Two cruisers, or a carrier plus two fighters (0.5 each), reach the threshold.
        assertThat(ThirtyKExploreMath.sympathiesEarnsVictoryPoint(4.0)).isTrue();
        assertThat(ThirtyKExploreMath.sympathiesEarnsVictoryPoint(8)).isTrue();
    }

    @Test
    void wastefulPiratesPaysOnlyForHitsThatCanBeAssigned() {
        assertThat(ThirtyKExploreMath.wastefulPiratesTradeGoods(3, 5)).isEqualTo(3);
        assertThat(ThirtyKExploreMath.wastefulPiratesTradeGoods(4, 2)).isEqualTo(2);
        assertThat(ThirtyKExploreMath.wastefulPiratesTradeGoods(2, 0)).isZero();
    }

    @Test
    void crossSpeciesMinglingAddsOne() {
        assertThat(ThirtyKExploreMath.crossSpeciesInfantry(0)).isEqualTo(1);
        assertThat(ThirtyKExploreMath.crossSpeciesInfantry(3)).isEqualTo(4);
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

    @Test
    void attachmentsReadyAgainAfterTheStatusPhase() {
        String actionPhase = ThirtyKExploreMath.readyCycleMarker(3, "action");
        String statusPhase = ThirtyKExploreMath.readyCycleMarker(3, "statusScoring");
        String agendaPhase = ThirtyKExploreMath.readyCycleMarker(3, "agendaVoting");
        String nextRound = ThirtyKExploreMath.readyCycleMarker(4, "strategy");
        assertThat(actionPhase).isNotEqualTo(statusPhase);
        assertThat(statusPhase).isEqualTo(agendaPhase);
        assertThat(nextRound).isNotEqualTo(actionPhase).isNotEqualTo(statusPhase);
    }

    @Test
    void everyPlayAreaExploreHasAFakeRelicWithTheSameId() {
        for (String id : ThirtyKExplores.PLAY_AREA_CARDS) {
            ExploreModel explore = Mapper.getExplore(id);
            RelicModel relic = Mapper.getRelic(id);
            assertThat(explore).as(id).isNotNull();
            assertThat(explore.getSource()).isEqualTo(ComponentSource.thirtyk);
            assertThat(relic).as(id).isNotNull();
            assertThat(relic.isFakeRelic()).as(id).isTrue();
        }
    }

    @Test
    void precisionLaserDefensesCarriesSpaceCannonThree() {
        var attachment = Mapper.getAttachmentInfo("precisionlaserdefenses30k");
        assertThat(attachment).isNotNull();
        assertThat(attachment.getSpaceCannonHitsOn()).isEqualTo(3);
        assertThat(attachment.getSpaceCannonDieCount()).isEqualTo(1);
    }
}
