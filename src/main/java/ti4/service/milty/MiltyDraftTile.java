package ti4.service.milty;

import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @JsonIgnore
    public boolean hasAnyWormhole() {
        return hasAlphaWH || hasBetaWH || hasOtherWH;
    }

    public double abstractValue() {
        double value = milty_res * 0.8 + milty_inf * 0.9 + milty_flex;
        value += isLegendary ? 1.5 : 0.0;
        if (tierList.isBlue()) {
            value += (hasAlphaWH || hasBetaWH) ? 0.5 : 0.0;
            value += hasOtherWH ? 1.5 : 0.0;
        } else {
            // Can't get multiple novae
            value -= tile.isSupernova() ? 1.0 : 0.0;
            // Can't get multiple rifts
            value += tile.isGravityRift() ? 0.5 : 0.0;
        }
        return value;
    }

    public void addPlanet(Planet planet) {
        int r = planet.getResources();
        int i = planet.getInfluence();

        resources += r;
        influence += i;

        if (r > i)
            milty_res += r;
        else if (i > r)
            milty_inf += i;
        else
            milty_flex += r;

        if (planet.isLegendary()) isLegendary = true;
    }
}
