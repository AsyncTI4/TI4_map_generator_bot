package ti4.service.draft;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.helpers.ListHelper;
import ti4.map.Tile;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.MahactKingDraftable;
import ti4.service.draft.draftables.MantisTileDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class TestData {
    // Constants to direct this class to the test resources directory
    private static final String TEST_STORAGE_DIRECTORY =
            Path.of("src", "test", "resources", "strings").toString();

    // Filenames used in tests (extension is assumed to be .txt)
    public static final String FINISHED_6P_DRAFT_FILE = "finished-6p-draft";

    public FactionDraftable createFactionDraftable() {
        FactionDraftable draftable = new FactionDraftable();
        draftable.addFaction("xxcha");
        draftable.addFaction("yssaril");
        draftable.addFaction("sol");
        draftable.addFaction("mentak");
        draftable.addFaction("keleresm");
        draftable.addFaction("nekro");
        draftable.addFaction("naaz");
        draftable.setKeleresFlavor("xxcha");
        return draftable;
    }

    public SpeakerOrderDraftable createSpeakerOrderDraftable() {
        SpeakerOrderDraftable draftable = new SpeakerOrderDraftable();
        draftable.setNumPicks(6);
        return draftable;
    }

    public SeatDraftable createSeatDraftable() {
        SeatDraftable draftable = new SeatDraftable();
        draftable.setNumSeats(6);
        return draftable;
    }

    public SliceDraftable createSliceDraftable() {
        SliceDraftable draftable = new SliceDraftable();
        List<MiltyDraftSlice> slices = new ArrayList<>();
        int tileId = 19;
        for (int i = 0; i < 6; ++i) {
            MiltyDraftSlice slice = new MiltyDraftSlice();
            slice.setName("" + (char) ('A' + i));
            slice.setTiles(new ArrayList<>());
            MiltyDraftTile tile = new MiltyDraftTile();
            tile.setTile(new Tile("" + tileId++, "none"));
            // Could set a lot more tile stats if needed by tests
            slice.getTiles().add(tile);
            slices.add(slice);
        }
        draftable.initialize(slices);
        return draftable;
    }

    public MantisTileDraftable createMantisTileDraftable() {
        MantisTileDraftable draftable = new MantisTileDraftable();
        List<Integer> tileIds = ListHelper.listOfIntegers(20, 62);

        draftable.load(String.join(",", tileIds.stream().map(Object::toString).toList()));
        draftable.setMulligans(2);
        draftable.setExtraBlues(1);
        draftable.setExtraReds(1);
        draftable.getMulliganTileIDs().add("20");
        draftable.getMulliganTileIDs().add("21");
        draftable.getDiscardedTileIDs().add("22");
        return draftable;
    }

    public MahactKingDraftable createMahactKingDraftable() {
        MahactKingDraftable draftable = new MahactKingDraftable();
        draftable.initialize(6, List.of(ComponentSource.twilights_fall), List.of(), List.of());
        return draftable;
    }

    public AndcatReferenceCardsDraftable createAndcatReferenceCardsDraftable() {
        AndcatReferenceCardsDraftable draftable = new AndcatReferenceCardsDraftable();
        draftable.initialize(
                6,
                List.of(ComponentSource.base, ComponentSource.pok, ComponentSource.thunders_edge),
                List.of(),
                List.of());
        return draftable;
    }

    public PublicSnakeDraftOrchestrator createPublicSnakeDraftOrchestrator() {
        PublicSnakeDraftOrchestrator orchestrator = new PublicSnakeDraftOrchestrator();
        orchestrator.setCurrentPlayerIndex(0);
        orchestrator.setReversing(false);
        return orchestrator;
    }

    public void setOrchestratorPlayerState(DraftOrchestrator orchestrator, DraftManager manager) {
        if (orchestrator instanceof PublicSnakeDraftOrchestrator psdo) {
            int orderIndex = 0;
            for (String userId : manager.getPlayerStates().keySet()) {
                PublicSnakeDraftOrchestrator.State state = new PublicSnakeDraftOrchestrator.State();
                state.setOrderIndex(orderIndex++);
                PlayerDraftState pState = manager.getPlayerStates().get(userId);
                pState.setOrchestratorState(state);
            }
            // Random position in draft
            psdo.setCurrentPlayerIndex(orderIndex / 2);
            psdo.setReversing(true);
        } else {
            throw new IllegalArgumentException(
                    "Setup test player state data: " + orchestrator.getClass().getSimpleName());
        }
    }

    public String getTestFile(String stringsTestFile) {
        try {

            Path path = Paths.get(TEST_STORAGE_DIRECTORY, stringsTestFile + Constants.TXT);
            if (!Files.exists(path)) {
                throw new RuntimeException(
                        "Test data file for finished 6p draft is missing at path: " + path.toAbsolutePath());
            }
            String firstLine =
                    Files.readAllLines(path, Charset.defaultCharset()).get(0);
            return firstLine;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read finished 6p draft string from file", e);
        }
    }
}
