package ti4.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class MapperLookupIndexTest extends BaseTi4Test {

    // getColor used to scan the colours in order, matching alias, then name, then any alias, per colour. The index
    // must pick the same colour for every name any colour answers to.
    @Test
    void colorIndexMatchesTheOldScanForEveryKnownName() {
        Set<String> names = new LinkedHashSet<>();
        for (ColorModel color : Mapper.getColors()) {
            names.add(color.getAlias());
            names.add(color.getName());
            names.addAll(color.getAliases());
        }
        for (String name : names) {
            assertEquals(scan(name), Mapper.getColor(name), name);
        }
    }

    @Test
    void unknownAndNullColorsResolveToNothing() {
        assertNull(Mapper.getColor("not-a-colour"));
        assertNull(Mapper.getColor("null"));
        assertNull(Mapper.getColor(null));
    }

    // Reinforcements and the website player area iterate this set, so its order must stay what rebuilding it gave.
    @Test
    void unitIdListKeepsTheOrderARebuildWouldGive() {
        Set<String> rebuilt =
                Mapper.getUnits().values().stream().map(UnitModel::getAsyncId).collect(Collectors.toSet());

        assertEquals(new ArrayList<>(rebuilt), new ArrayList<>(Mapper.getUnitIDList()));
    }

    private static ColorModel scan(String name) {
        List<ColorModel> colors = Mapper.getColors();
        for (ColorModel color : colors) {
            if (color.getAlias().equals(name)) return color;
            if (color.getName().equals(name)) return color;
            if (color.getAliases().contains(name)) return color;
        }
        return null;
    }
}
