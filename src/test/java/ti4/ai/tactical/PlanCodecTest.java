package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.helpers.Units.UnitType;
import ti4.service.testbed.TestBedService;

// Plans are kept in the AI's memory between presses, so their text form must round-trip exactly.
class PlanCodecTest {

    @Test
    void tacticalPlanRoundTrips() {
        Map<String, Integer> landings = new LinkedHashMap<>();
        landings.put("lodor", 1);
        TacticalPlan plan = new TacticalPlan(
                Kind.EXPAND,
                "201",
                List.of(
                        new UnitMove("301", "space", UnitType.Carrier, 1),
                        new UnitMove("301", "mordaiii", UnitType.Infantry, 1)),
                landings,
                3.4);

        assertThat(TacticalPlan.decode(plan.encode())).contains(plan);
        assertThat(TestBedService.isSaveSafe(plan.encode())).isTrue();
    }

    @Test
    void producePlanWithoutMovesRoundTrips() {
        TacticalPlan plan = new TacticalPlan(Kind.PRODUCE, "301", List.of(), Map.of(), 2.5);

        assertThat(TacticalPlan.decode(plan.encode())).contains(plan);
    }

    @Test
    void buildPlanRoundTrips() {
        BuildPlan plan = new BuildPlan(List.of(
                new BuildOrder("carrier", "301", UnitType.Carrier, 1, 3.0, 2.5, false),
                new BuildOrder("2gf", "mordaiii", UnitType.Infantry, 2, 1.0, 0.4, true)));

        assertThat(BuildPlan.decode(plan.encode())).contains(plan);
    }

    @Test
    void garbageDecodesToNothing() {
        assertThat(TacticalPlan.decode("not a plan")).isEmpty();
    }
}
