package ti4.service.milty;

import lombok.Data;
import ti4.map.Planet;
import ti4.map.Tile;

@Data
public class MiltyDraftTile {

    private Tile tile;
    private TierList tierList;
    private int resources;
    private int influence;
    private boolean hasAlphaWH;
    private boolean hasBetaWH;
    private boolean hasOtherWH;
    private boolean isLegendary;

    private int milty_res;
    private int milty_inf;
    private int milty_flex;

    public void addPlanet(Planet planet) {
        int r = planet.getResources();
        int i = planet.getInfluence();

        resources += r;
        influence += i;

        if (r > i) milty_res += r;
        else if (i > r) milty_inf += i;
        else milty_flex += r;

        if (planet.isLegendary()) isLegendary = true;
    }
}
