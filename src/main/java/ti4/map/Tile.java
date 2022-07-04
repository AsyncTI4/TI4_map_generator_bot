package ti4.map;

import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.helpers.LoggerHandler;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.StringTokenizer;

public class Tile {
    private final String tileID;
    private final String position;
    private HashMap<String, UnitHolder> unitHolders = new HashMap();

    public Tile(String tileID, String position) {
        this.tileID = tileID;
        this.position = position != null ? position.toLowerCase() : null;
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
    public Path getUnitPath(String unitID) {
        Path unitPath = ResourceHelper.getInstance().getUnitFile(unitID);
        if (unitPath == null) {
            LoggerHandler.log("Could not find unit: " + unitID);
            return null;
        }
        return unitPath;
    }

    @CheckForNull
    public Path getCCPath(String ccID) {
       return Mapper.getCCPath(ccID);
    }

    @CheckForNull
    public Path getAttachmentPath(String tokenID) {
        Path tokenPath = ResourceHelper.getInstance().getAttachmentFile(tokenID);
        if (tokenPath == null) {
//            LoggerHandler.log("Could not find attachment token: " + tokenID);
            return null;
        }
        return tokenPath;
    }

    @CheckForNull
    public Path getTokenPath(String tokenID) {
        return Mapper.getTokenPath(tokenID);
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

     public void addUnitDamage(String spaceHolder, String unitID, @CheckForNull Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null && count != null) {
            HashMap<String, Integer> units = unitHolder.getUnits();
            Integer unitCount = units.get(unitID);
            if (unitCount != null) {
                if (unitCount < count){
                    count = unitCount;
                }
                unitHolder.addUnitDamage(unitID, count);
            }
        }
    }

    public void addCC(String ccID) {
        UnitHolder unitHolder = unitHolders.get(Constants.SPACE);
        if (unitHolder != null) {
            unitHolder.addCC(ccID);
        }
    }

    public void addControl(String ccID, String spaceHolder) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.addControl(ccID);
        }
    }
    public void removeControl(String tokenID, String spaceHolder) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.removeControl(tokenID);
        }
    }

    public void addToken(String tokenID, String spaceHolder) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.addToken(tokenID);
        }
    }
    public void removeToken(String tokenID, String spaceHolder) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.removeToken(tokenID);
        }
    }

    public void removeCC(String ccID) {
        UnitHolder unitHolder = unitHolders.get(Constants.SPACE);
        if (unitHolder != null) {
            unitHolder.removeCC(ccID);
        }
    }

    public void removeAllCC() {
        UnitHolder unitHolder = unitHolders.get(Constants.SPACE);
        if (unitHolder != null) {
            unitHolder.removeAllCC();
        }
    }

    public void removeUnit(String spaceHolder, String unitID, Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.removeUnit(unitID, count);
        }
    }

    public void removeUnitDamage(String spaceHolder, String unitID, @CheckForNull Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null && count != null) {
            unitHolder.removeUnitDamage(unitID, count);
        }
    }

    public void removeAllUnits(String color) {
        for (UnitHolder unitHolder : unitHolders.values()) {
            unitHolder.removeAllUnits(color);
            unitHolder.removeAllUnitDamage(color);
        }
    }

    public void removeAllUnitDamage(String color) {
        for (UnitHolder unitHolder : unitHolders.values()) {
            unitHolder.removeAllUnitDamage(color);
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

    public void addUnitDamage(String spaceHolder, String unitID, String count) {
        try {
            int unitCount = Integer.parseInt(count);
            addUnitDamage(spaceHolder, unitID, unitCount);
        } catch (Exception e) {
            LoggerHandler.log("Could not parse unit count", e);
        }
    }

    public String getTileID() {
        return tileID;
    }

    public String getPosition() {
        return position != null ? position.toLowerCase() : null;
    }

    public Path getTilePath() {
        String tileName = Mapper.getTileID(tileID);
        Path tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            LoggerHandler.log("Could not find tile: " + tileID);
        }
        return tilePath;
    }

    public HashMap<String, UnitHolder> getUnitHolders() {
        return unitHolders;
    }


}
