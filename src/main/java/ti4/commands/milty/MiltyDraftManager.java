package ti4.commands.milty;

import ti4.map.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiltyDraftManager {

    private List<MiltyDraftTile> high = new ArrayList<>();
    private List<MiltyDraftTile> mid = new ArrayList<>();
    private List<MiltyDraftTile> low = new ArrayList<>();
    private List<MiltyDraftTile> red = new ArrayList<>();

    private List<MiltyDraftSlice> slices = new ArrayList<>();

    private List<Player> draftOrder = new ArrayList<>();
    private int draftIndex = 0;
    private List<Player> draftRandomOrder = new ArrayList<>();
    private Map<Player, PlayerDraft> draft = new HashMap<>();

    private List<String> factionDraft = new ArrayList<>();

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

    public Player getDraftOrderPlayer(){
        return draftOrder.get(draftIndex);
    }

    public void setNextPlayerInDraft(){
        draftIndex++;
    }

    public List<MiltyDraftTile> getHigh() {
        return new ArrayList<>(high);
    }

    public List<String> getFactionDraft() {
        return factionDraft;
    }

    public void setFactionDraft(List<String> factionDraft) {
        this.factionDraft = factionDraft;
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

    public void setDraftOrder(List<Player> draftOrder){
        this.draftOrder = draftOrder;
    }

    public void setDraftRandomOrder(List<Player> draftOrder){
        this.draftRandomOrder = draftOrder;

        for (Player player : draftOrder) {
            draft.put(player, new PlayerDraft());
        }
    }

    public List<Player> getDraftRandomOrder() {
        return draftRandomOrder;
    }

    public PlayerDraft getPlayerDraft(Player player){
        return draft.get(player);
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

    private static class PlayerDraft {
        private String faction;
        private MiltyDraftSlice slice;
        private int order;

        public String getFaction() {
            return faction;
        }

        public void setFaction(String faction) {
            this.faction = faction;
        }

        public MiltyDraftSlice getSlice() {
            return slice;
        }

        public void setSlice(MiltyDraftSlice slice) {
            this.slice = slice;
        }

        public int getOrder() {
            return order;
        }

        public void setOrder(int order) {
            this.order = order;
        }
    }

}
