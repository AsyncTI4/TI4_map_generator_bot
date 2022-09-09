package ti4.commands.milty;

import java.util.ArrayList;
import java.util.List;

public class MiltyDraftManager {

    private List<MiltyDraftTile> high = new ArrayList<>();
    private List<MiltyDraftTile> mid = new ArrayList<>();
    private List<MiltyDraftTile> low = new ArrayList<>();
    private List<MiltyDraftTile> red = new ArrayList<>();

    private List<MiltyDraftSlice> slices = new ArrayList<>();

    public void addDraftTile(MiltyDraftTile draftTile) {
        TierList draftTileTier = draftTile.getTierList();
        if (draftTileTier == TierList.high) {
            high.add(draftTile);
        } else if (draftTileTier == TierList.mid) {
            mid.add(draftTile);
        } else if (draftTileTier == TierList.low) {
            low.add(draftTile);
        } else if (draftTileTier == TierList.red) {
            red.add(draftTile);
        } else if (draftTileTier == TierList.anomaly) {
            red.add(draftTile);
        }
    }

    public List<MiltyDraftTile> getHigh() {
        return new ArrayList<>(high);
    }

    public List<MiltyDraftTile> getMid() {
        return new ArrayList<>(mid);
    }

    public List<MiltyDraftTile> getLow() {
        return new ArrayList<>(low);
    }

    public List<MiltyDraftTile> getRed() {
        return new ArrayList<>(red);
    }

    public void addSlice(MiltyDraftSlice slice) {
        slices.add(slice);
    }

    public List<MiltyDraftSlice> getSlices() {
        return slices;
    }

    public void clearSlices(){
        slices.clear();
    }

    public void clear() {
        clearSlices();
        high.clear();
        mid.clear();
        low.clear();
        red.clear();
    }
}
