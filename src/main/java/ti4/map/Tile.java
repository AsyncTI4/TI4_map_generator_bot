package ti4.map;

import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
import java.util.HashMap;

public class Tile {
    private String tileID;
    private String position;
    private String tilePath;
    private HashMap<String, String> units = new HashMap<>();
    private HashMap<String, String> unitCache = new HashMap<>();


    public Tile(String tileID, String position) {
        this.tileID = tileID;
        this.position = position;
    }

    public void setUnit(String position, String unitID) {
        units.put(position, unitID);
    }

    public String getTileID() {
        return tileID;
    }

    public String getPosition() {
        return position;
    }

    public String getTilePath() {
        if (tilePath != null)
        {
            return tilePath;
        }
        String tileName = Mapper.getTileID(tileID);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null)
        {
            LoggerHandler.log("Could not find tile: " + tileID);
        }
        return tilePath;
    }

    public HashMap<String, String> getUnits() {
        return units;
    }

    @CheckForNull
    public String getUnitPath(String unitID) {
        String unitPath = unitCache.get(unitID);
        if (unitPath != null)
        {
            return unitPath;
        }
        unitPath = ResourceHelper.getInstance().getUnitFile(unitID);
        if (unitPath == null)
        {
            LoggerHandler.log("Could not find unit: " + unitID);
            return null;
        }
        unitCache.put(unitID, unitPath);
        return unitPath;
    }
}
