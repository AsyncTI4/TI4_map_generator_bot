package ti4.service.milty;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.image.Mapper;
import ti4.model.MapTemplateModel;
import ti4.model.Source.ComponentSource;
import ti4.testUtils.BaseTi4Test;

class SliceFeasibilityRealTilesTest extends BaseTi4Test {

    @Test
    void defaultSettingsAreNeverRejectedForTheRealTilePool() {
        // The check must only reject settings no shuffle could satisfy; the default settings are known to work
        // for every slice count the bot allows, so a rejection here means a bound is wrong.
        for (List<ComponentSource> sources : List.of(
                List.of(ComponentSource.base, ComponentSource.pok),
                List.of(
                        ComponentSource.base,
                        ComponentSource.pok,
                        ComponentSource.ds,
                        ComponentSource.uncharted_space,
                        ComponentSource.thunders_edge))) {
            MiltyDraftManager draftManager = draftManagerFor(sources);
            int maxSlices = Math.min(
                    draftManager.getRed().size() / 2, draftManager.getBlue().size() / 3);
            for (int sliceCount = 6; sliceCount <= maxSlices; sliceCount++) {
                assertThat(findImpossibility(draftManager, specFor(sliceCount)))
                        .as("%d slices from %s", sliceCount, sources)
                        .isEmpty();
            }
        }
    }

    @Test
    void askingForMoreLegendariesThanThePoolHasIsRejected() {
        // Base + PoK only has two legendary planets.
        MiltyDraftManager draftManager = draftManagerFor(List.of(ComponentSource.base, ComponentSource.pok));
        MiltyDraftSpec spec = specFor(6);
        spec.minLegend = 3;
        spec.maxLegend = 3;

        assertThat(findImpossibility(draftManager, spec))
                .contains("at most 2 legendary planets can end up in the slices, but the minimum is 3");
    }

    private static Optional<String> findImpossibility(MiltyDraftManager draftManager, MiltyDraftSpec spec) {
        MapTemplateModel template = Mapper.getDefaultMapTemplateForPlayerCount(6);
        return SliceFeasibility.findImpossibility(
                GenerateSlicesService.partitionIntoTiers(draftManager, template),
                spec.numSlices,
                GenerateSlicesService.requiredExtraWormholes(draftManager, spec),
                spec);
    }

    private static MiltyDraftManager draftManagerFor(List<ComponentSource> sources) {
        MiltyDraftManager draftManager = new MiltyDraftManager();
        draftManager.init(sources);
        return draftManager;
    }

    private static MiltyDraftSpec specFor(int sliceCount) {
        MiltyDraftSpec spec = new MiltyDraftSpec(new Game());
        spec.numSlices = sliceCount;
        spec.playerIDs = Collections.nCopies(6, "player");
        return spec;
    }
}
