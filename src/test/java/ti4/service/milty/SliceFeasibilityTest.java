package ti4.service.milty;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import ti4.game.Game;

class SliceFeasibilityTest {

    private static final int SLICES = 2;

    // Baseline: five tiers of three tiles, every tile worth 1 resource + 1 influence, one legendary.
    // Each slice then has 5 res / 5 inf / 10 total, which satisfies the default spec (2 res, 3 inf, 9-13 total).
    private final List<List<MiltyDraftTile>> tiers = uniformTiers(5, 3);
    private final MiltyDraftSpec spec = specFor(SLICES);

    @Test
    void satisfiableSettingsAreAllowed() {
        tiers.get(0).get(0).setLegendary(true);

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec)).isEmpty();
    }

    @Test
    void aTierWithTooFewTilesCannotFillEverySlice() {
        tiers.get(0).get(0).setLegendary(true);
        tiers.set(4, new ArrayList<>(List.of(tile(t -> {}))));

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains("the tile tiers only hold enough tiles for 1 slice");
    }

    @Test
    void tooFewLegendariesForTheMinimum() {
        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains("at most 0 legendary planets can end up in the slices, but the minimum is 1");
    }

    @Test
    void oneLegendaryPerSliceCapAppliesWhenTheMaximumIsBelowTheSliceCount() {
        // Legendaries in two tiers could give 4, but with maxLegend < numSlices each slice may hold only one.
        markAll(tiers.get(0), t -> t.setLegendary(true));
        markAll(tiers.get(1), t -> t.setLegendary(true));
        spec.minLegend = 3;
        spec.maxLegend = 1;

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains("at most 2 legendary planets can end up in the slices, but the minimum is 3");
    }

    @Test
    void tooManyForcedLegendariesForTheMaximum() {
        // Every tile in two tiers is legendary, so each of the 2 slices gets 2 of them no matter the shuffle.
        markAll(tiers.get(0), t -> t.setLegendary(true));
        markAll(tiers.get(1), t -> t.setLegendary(true));

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains("at least 4 legendary planets must end up in the slices, but the maximum is 2");
    }

    @Test
    void tooFewWormholesForExtraWormholes() {
        tiers.get(0).get(0).setLegendary(true);
        tiers.get(1).get(0).setHasAlphaWH(true);
        tiers.get(2).get(0).setHasBetaWH(true);

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 3, spec))
                .contains("at most 2 wormholes can end up in the slices, but extra wormholes needs 3");
    }

    @Test
    void forcedDoubleAlphaInASlice() {
        tiers.get(0).get(0).setLegendary(true);
        markAll(tiers.get(1), t -> t.setHasAlphaWH(true));
        markAll(tiers.get(2), t -> t.setHasAlphaWH(true));

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains("some slice would have to hold two alpha or two beta wormholes");
    }

    @Test
    void minimumTotalAboveTheBestPossibleSlices() {
        tiers.get(0).get(0).setLegendary(true);
        spec.minTot = 11;

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains("even the best tiles average 10.0 optimal total value per slice, below the minimum of 11");
    }

    @Test
    void maximumTotalBelowTheWeakestPossibleSlices() {
        tiers.get(0).get(0).setLegendary(true);
        spec.maxTot = 9;

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains("even the weakest tiles average 10.0 optimal total value per slice, above the maximum of 9");
    }

    @Test
    void minimumInfluenceAboveTheBestPossibleSlices() {
        tiers.get(0).get(0).setLegendary(true);
        spec.minInf = 6.0;

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains(
                        "even the highest-influence tiles average 5.0 optimal influence per slice, below the minimum of 6.0");
    }

    @Test
    void scarBonusCountsTowardResources() {
        // 5 resources per slice; a minimum of 6 is only reachable through the +2 scar bonus.
        tiers.get(0).get(0).setLegendary(true);
        spec.minRes = 6.0;
        spec.maxTot = 20;

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec))
                .contains(
                        "even the highest-resource tiles average 5.0 optimal resources per slice, below the minimum of 6.0");

        markAll(tiers.get(4), t -> t.setHasScar(true));

        assertThat(SliceFeasibility.findImpossibility(tiers, SLICES, 0, spec)).isEmpty();
    }

    private static MiltyDraftSpec specFor(int sliceCount) {
        MiltyDraftSpec spec = new MiltyDraftSpec(new Game());
        spec.numSlices = sliceCount;
        return spec;
    }

    private static List<List<MiltyDraftTile>> uniformTiers(int tierCount, int tilesPerTier) {
        List<List<MiltyDraftTile>> tiers = new ArrayList<>();
        for (int tier = 0; tier < tierCount; tier++) {
            List<MiltyDraftTile> tiles = new ArrayList<>();
            for (int i = 0; i < tilesPerTier; i++) {
                tiles.add(tile(t -> {}));
            }
            tiers.add(tiles);
        }
        return tiers;
    }

    private static MiltyDraftTile tile(Consumer<MiltyDraftTile> customize) {
        MiltyDraftTile tile = new MiltyDraftTile();
        tile.setMiltyRes(1);
        tile.setMiltyInf(1);
        customize.accept(tile);
        return tile;
    }

    private static void markAll(List<MiltyDraftTile> tier, Consumer<MiltyDraftTile> mark) {
        tier.forEach(mark);
    }
}
