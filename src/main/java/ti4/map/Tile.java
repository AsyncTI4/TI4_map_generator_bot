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
    private final String tileID;
    private final String position;
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

    @CheckForNull
    public String getCCPath(String ccID) {
        String ccPath = ResourceHelper.getInstance().getCCFile(ccID);
        if (ccPath == null) {
            LoggerHandler.log("Could not find command counter: " + ccID);
            return null;
        }
        return ccPath;
    }

    @CheckForNull
    public String getAttachmentPath(String tokenID) {
        String tokenPath = ResourceHelper.getInstance().getAttachmentFile(tokenID);
        if (tokenPath == null) {
//            LoggerHandler.log("Could not find attachment token: " + tokenID);
            return null;
        }
        return tokenPath;
    }

    @CheckForNull
    public String getTokenPath(String tokenID) {
        String tokenPath = ResourceHelper.getInstance().getAttachmentFile(tokenID);
        if (tokenPath == null) {
            tokenPath = ResourceHelper.getInstance().getTokenFile(tokenID);
            if (tokenPath == null) {
                LoggerHandler.log("Could not find token: " + tokenID);
                return null;
            }
        }
        return tokenPath;
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

     public void addUnitDamage(String spaceHolder, String unitID, Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
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
            unitHolder.removeUnitDamage(unitID, count);
        }
    }

    public void removeUnitDamage(String spaceHolder, String unitID, Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
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
