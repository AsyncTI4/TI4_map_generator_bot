package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;

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

    public final static String SAVED_6P_FINISHED_DRAFT = "p:1372802966104576032,u0|1373125825154519090,u1|1373126269671051314,u2|1335385481092796499,u3|455013002953883651,u4|1373126734408585216,u5&o:PublicSnakeDraftOrchestrator|0,false&d:FactionDraftable|keleresflavor_null,xxcha,yin,winnu,l1z1x,hacan,keleresm,sardakk&d:SpeakerOrderDraftable|6&d:SeatDraftable|6&d:SliceDraftable|48,25,32;35,23,46;24,36,49;40,21,75;47,70,27;72,26,50;33,39,34&pp:u0|Seat|seat6&pp:u0|Slice|A&pp:u0|SpeakerOrder|pick6&pp:u0|Faction|winnu&pp:u1|Seat|seat5&pp:u1|Slice|B&pp:u1|SpeakerOrder|pick1&pp:u1|Faction|l1z1x&pp:u2|Seat|seat1&pp:u2|Slice|F&pp:u2|SpeakerOrder|pick2&pp:u2|Faction|hacan&pp:u3|Seat|seat3&pp:u3|Slice|E&pp:u3|SpeakerOrder|pick3&pp:u3|Faction|xxcha&pp:u4|Seat|seat4&pp:u4|Slice|C&pp:u4|SpeakerOrder|pick4&pp:u4|Faction|sardakk&pp:u5|Seat|seat2&pp:u5|Slice|D&pp:u5|SpeakerOrder|pick5&pp:u5|Faction|yin&po:u0|5&po:u1|1&po:u2|2&po:u3|3&po:u4|0&po:u5|4";
}
