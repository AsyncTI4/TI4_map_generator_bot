package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;

import de.gesundkrank.jskills.Player;
import lombok.experimental.UtilityClass;
import ti4.map.Tile;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SeatDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class TestData {
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
}
