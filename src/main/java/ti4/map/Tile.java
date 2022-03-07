package ti4.map;

import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.util.HashMap;
import java.util.StringTokenizer;

public class Tile {
    private String tileID;
    private String position;
    private HashMap<String, UnitHolder> unitHolders = new HashMap();

    public Tile(String tileID, String position) {
        this.tileID = tileID;
        this.position = position;
        initPlanetsAndSpace(tileID);
    }

    private void initPlanetsAndSpace(String tileID) {

        Space space = new Space(Constants.SPACE, Constants.SPACE_CENTER_POSITION);
        unitHolders.put(Constants.SPACE, space);
        String tilePlanetPositions = PositionMapper.getTilePlanetPositions(tileID);
        if (tilePlanetPositions != null) {
            StringTokenizer tokenizer = new StringTokenizer(tilePlanetPositions, ";");
            while (tokenizer.hasMoreTokens()) {
                String planetInfo = tokenizer.nextToken();
                if (planetInfo.length() > 4) {
                    StringTokenizer planetTokenizer = new StringTokenizer(planetInfo, " ");
                    String planetName = planetTokenizer.nextToken().toLowerCase();
                    Point planetPosition = PositionMapper.getPoint(planetTokenizer.nextToken());
                    Planet planet = new Planet(planetName, planetPosition);
                    unitHolders.put(planetName, planet);
                }
            }
        }
    }

    @CheckForNull
    public String getUnitPath(String unitID) {
        String unitPath = ResourceHelper.getInstance().getUnitFile(unitID);
        if (unitPath == null) {
            LoggerHandler.log("Could not find unit: " + unitID);
            return null;
        }
        return unitPath;
    }

    public boolean isSpaceHolderValid(String spaceHolder) {
        return unitHolders.get(spaceHolder) != null;
    }

    public void addUnit(String spaceHolder, String unitID, Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.addUnit(unitID, count);
        }
    }

    public void removeUnit(String spaceHolder, String unitID, Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.removeUnit(unitID, count);
        }
    }

    public void addUnit(String spaceHolder, String unitID, String count) {
        try {
            int unitCount = Integer.parseInt(count);
            addUnit(spaceHolder, unitID, unitCount);
        } catch (Exception e) {
            LoggerHandler.log("Could not parse unit count", e);
        }
    }

    public String getTileID() {
        return tileID;
    }

    public String getPosition() {
        return position;
    }

    public String getTilePath() {
        String tileName = Mapper.getTileID(tileID);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            LoggerHandler.log("Could not find tile: " + tileID);
        }
        return tilePath;
    }

    public HashMap<String, UnitHolder> getUnitHolders() {
        return unitHolders;
    }


}
