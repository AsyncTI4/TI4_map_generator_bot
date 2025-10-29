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
    private boolean hasScar;

    private int miltyRes;
    private int miltyInf;
    private int miltyFlex;

    @JsonIgnore
    public boolean hasAnyWormhole() {
        return hasAlphaWH || hasBetaWH || hasOtherWH;
    }

    // Some notes on this:
    // - I skipped value adding for stations because they dont count toward objectives
    public double abstractValue() {
        double value = miltyRes * 0.8 + miltyInf * 0.9 + miltyFlex;
        value += isLegendary ? 1.5 : 0.0;
        if (tierList.isBlue()) {
            value += (hasAlphaWH || hasBetaWH) ? 0.5 : 0.0;
            value += hasOtherWH ? 1.5 : 0.0;

            // +1 per skip
            int skips = 0;
            for (Planet p : tile.getPlanetUnitHolders())
                skips += p.getTechSpecialities().size();
            value += skips;
        } else {
            // Can't get multiple novae
            value -= tile.isSupernova() ? 1.0 : 0.0;

            // Can't get multiple rifts/scars
            value += hasScar ? 1.0 : 0.0;
            value += tile.isGravityRift() ? 0.5 : 0.0;
        }
        return value;
    }

    public void addPlanet(Planet planet) {
        int r = planet.getResources();
        int i = planet.getInfluence();

        resources += r;
        influence += i;

        if (r > i) miltyRes += r;
        else if (i > r) miltyInf += i;
        else miltyFlex += r;

        if (planet.isLegendary()) isLegendary = true;
    }
}
