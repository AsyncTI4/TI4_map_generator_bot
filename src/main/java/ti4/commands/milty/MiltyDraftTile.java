package ti4.commands.milty;

import ti4.map.Tile;

public class MiltyDraftTile {
    private Tile tile;
    private TierList tierList;
    private int resources = 0;
    private int influence = 0;
    private double milty_resources = 0.0;
    private double milty_influence = 0.0;
    private boolean hasAlphaWH = false;
    private boolean hasBetaWH = false;
    private boolean hasOtherWH = false;
    private boolean isLegendary = false;

    public Tile getTile() {
        return tile;
    }

    public void setTile(Tile tile) {
        this.tile = tile;
    }

    public TierList getTierList() {
        return tierList;
    }

    public void setTierList(TierList tierList) {
        this.tierList = tierList;
    }

    public int getResources() {
        return resources;
    }

    public void addResources(int resources) {
        this.resources += resources;
    }

    public int getInfluence() {
        return influence;
    }

    public void addInfluence(int influence) {
        this.influence += influence;
    }

    public double getMilty_resources() {
        return milty_resources;
    }

    public void addMilty_resources(double milty_resources) {
        this.milty_resources += milty_resources;
    }

    public double getMilty_influence() {
        return milty_influence;
    }

    public void addMilty_influence(double milty_influence) {
        this.milty_influence += milty_influence;
    }

    public boolean isHasAlphaWH() {
        return hasAlphaWH;
    }

    public void setHasAlphaWH(boolean hasAlphaWH) {
        this.hasAlphaWH = hasAlphaWH;
    }

    public boolean isHasBetaWH() {
        return hasBetaWH;
    }

    public void setHasBetaWH(boolean hasBetaWH) {
        this.hasBetaWH = hasBetaWH;
    }

    public void setHasOtherWH(boolean hasOtherWH) {
        this.hasOtherWH = hasOtherWH;
    }

    public boolean isLegendary() {
        return isLegendary;
    }

    public void setLegendary(boolean legendary) {
        isLegendary = legendary;
    }
}
