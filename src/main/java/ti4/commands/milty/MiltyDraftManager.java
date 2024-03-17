package ti4.commands.milty;

import ti4.map.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiltyDraftManager {

    private final List<MiltyDraftTile> blue = new ArrayList<>();
    private final List<MiltyDraftTile> red = new ArrayList<>();

    private final List<MiltyDraftSlice> slices = new ArrayList<>();

    private List<Player> draftOrder = new ArrayList<>();
    private int draftIndex;
    private List<Player> draftRandomOrder = new ArrayList<>();
    private final Map<Player, PlayerDraft> draft = new HashMap<>();

    private List<String> factionDraft = new ArrayList<>();

    public void addDraftTile(MiltyDraftTile draftTile) {
        TierList draftTileTier = draftTile.getTierList();
        switch (draftTileTier) {
            case high, mid, low -> blue.add(draftTile);
            case red, anomaly -> red.add(draftTile);
        }
    }

    public Player getDraftOrderPlayer() {
        return draftOrder.get(draftIndex);
    }

    public void setNextPlayerInDraft() {
        draftIndex++;
    }

    public List<String> getFactionDraft() {
        return factionDraft;
    }

    public void setFactionDraft(List<String> factionDraft) {
        this.factionDraft = factionDraft;
    }

    public List<MiltyDraftTile> getBlue() {
        return new ArrayList<>(blue);
    }

    public List<MiltyDraftTile> getRed() {
        return new ArrayList<>(red);
    }

    public void addSlice(MiltyDraftSlice slice) {
        slices.add(slice);
    }

    public void setDraftOrder(List<Player> draftOrder) {
        this.draftOrder = draftOrder;
    }

    public void setDraftRandomOrder(List<Player> draftOrder) {
        draftRandomOrder = draftOrder;

        for (Player player : draftOrder) {
            draft.put(player, new PlayerDraft());
        }
    }

    public List<Player> getDraftRandomOrder() {
        return draftRandomOrder;
    }

    public PlayerDraft getPlayerDraft(Player player) {
        return draft.get(player);
    }

    public List<MiltyDraftSlice> getSlices() {
        return slices;
    }

    public void clearSlices() {
        slices.clear();
    }

    public void clear() {
        clearSlices();
        blue.clear();
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
