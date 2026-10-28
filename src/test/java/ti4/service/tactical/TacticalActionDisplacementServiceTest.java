package ti4.service.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;

class TacticalActionDisplacementServiceTest {

    @Test
    void removeEmptyDisplacementDropsNullStatesAndEmptyHolders() {
        UnitKey carrier = new UnitKey(UnitType.Carrier, "psn");
        UnitKey fighter = new UnitKey(UnitType.Fighter, "psn");

        // A stale "Move 1 Carrier" press used to store a null state list, which crashed button refresh.
        Map<UnitKey, List<Integer>> onlyNull = new HashMap<>();
        onlyNull.put(carrier, null);

        Map<UnitKey, List<Integer>> mixed = new HashMap<>();
        mixed.put(carrier, null);
        mixed.put(fighter, new ArrayList<>(List.of(2, 0, 0, 0)));

        Map<String, Map<UnitKey, List<Integer>>> displacement = new HashMap<>();
        displacement.put("313-space", onlyNull);
        displacement.put("314-space", mixed);
        displacement.put("315-space", null);

        TacticalActionDisplacementService.removeEmptyDisplacement(displacement);

        assertThat(displacement).containsOnlyKeys("314-space");
        assertThat(displacement.get("314-space")).containsOnlyKeys(fighter);
    }
}
