package ti4.map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.message.BotLogger;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public class Tile {
    private final String tileID;
    private String position;
    private HashMap<String, UnitHolder> unitHolders = new HashMap<>();
    
    private HashMap<Player,Boolean> fog = new HashMap<>();
    private HashMap<Player,String> fogLabel = new HashMap<>();

    public Tile(String tileID, String position) {
        this.tileID = tileID;
        this.position = position != null ? position.toLowerCase() : null;
        initPlanetsAndSpace(tileID);
    }

    public Tile(String tileID, String position, Player player, Boolean fog_, String fogLabel_) {
        this.tileID = tileID;
        this.position = position != null ? position.toLowerCase() : null;
        if(player != null)
        {
        	fog.put(player, fog_);
        	fogLabel.put(player, fogLabel_);
        }
        initPlanetsAndSpace(tileID);
    }

    public Tile(String tileID, String position, UnitHolder spaceHolder) {
        this.tileID = tileID;
        this.position = position != null ? position.toLowerCase() : null;
        initPlanetsAndSpace(tileID);
        unitHolders.replace(Constants.SPACE, spaceHolder);
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

    @Nullable
    public static String getUnitPath(String unitID) {
        String unitPath = ResourceHelper.getInstance().getUnitFile(unitID);
        if (unitPath == null) {
            BotLogger.log("Could not find unit: " + unitID);
            return null;
        }
        return unitPath;
    }

    @Nullable
    public String getCCPath(String ccID) {
       return Mapper.getCCPath(ccID);
    }

    @Nullable
    public String getAttachmentPath(String tokenID) {
        String tokenPath = ResourceHelper.getInstance().getAttachmentFile(tokenID);
        if (tokenPath == null) {
//            LoggerHandler.log("Could not find attachment token: " + tokenID);
            return null;
        }
        return tokenPath;
    }

    @Nullable
    public String getTokenPath(String tokenID) {
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

     public void addUnitDamage(String spaceHolder, String unitID, @Nullable Integer count) {
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

    public boolean hasCC(String ccID) {
        UnitHolder unitHolder = unitHolders.get(Constants.SPACE);
        if (unitHolder != null) {
            return unitHolder.hasCC(ccID);
        }
        return false;
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

    public void removeUnitDamage(String spaceHolder, String unitID, @Nullable Integer count) {
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
            BotLogger.log("Could not parse unit count", e);
        }
    }

    public void addUnitDamage(String spaceHolder, String unitID, String count) {
        try {
            int unitCount = Integer.parseInt(count);
            addUnitDamage(spaceHolder, unitID, unitCount);
        } catch (Exception e) {
            BotLogger.log("Could not parse unit count", e);
        }
    }

    public List<Boolean> getHyperlaneData(Integer sourceDirection) {
        List<List<Boolean>> fullHyperlaneData = Mapper.getHyperlaneData(this.tileID);
        if (fullHyperlaneData == null || fullHyperlaneData.size() == 0) {
            return null;
        } else if (sourceDirection < 0 || sourceDirection > 5) {
            return Collections.emptyList();
        }
        return fullHyperlaneData.get(sourceDirection);
    }

    public String getTileID() {
        return tileID;
    }

    public String getPosition() {
        return position != null ? position.toLowerCase() : null;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getTilePath() {
        String tileName = Mapper.getTileID(tileID);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            BotLogger.log("Could not find tile: " + tileID);
        }
        return tilePath;
    }

    public boolean hasFog(Player player) {
        Boolean hasFog = fog.get(player);
        //default all tiles to being foggy to prevent unintended info leaks
        return hasFog == null || hasFog;
    }

    public void setTileFog(@NotNull Player player, Boolean fog_) {
        fog.put(player, fog_);
    }

    public String getFogLabel(Player player) {
        return fogLabel.get(player);
    }

    public void setFogLabel(@NotNull Player player, String fogLabel_) {
        fogLabel.put(player, fogLabel_);
    }

    public String getFowTilePath(Player player) {
        String fogTileColor = player == null ? "default" : player.getFogFilter();
        String fogTileColorSuffix = "_" + fogTileColor;
        String fowTileID = "fow" + fogTileColorSuffix;
        
        if(this.tileID.equals("82b") || this.tileID.equals("51")) { //mallice || creuss
            fowTileID = "fowb" + fogTileColorSuffix;
        }
        if(this.tileID.equals("82a")) { //mallicelocked
            fowTileID = "fowc" + fogTileColorSuffix;
        }

        String tileName = Mapper.getTileID(fowTileID);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            BotLogger.log("Could not find tile: " + fowTileID);
        }
        return tilePath;
    }

    public HashMap<String, UnitHolder> getUnitHolders() {
        return unitHolders;
    }

    public String getRepresentation() {
        try {
            return Mapper.getTileRepresentations().get(getTileID());     
        } catch (Exception e) {
            // TODO: handle exception
        }
        return null;
    }

    public String getRepresentationForAutoComplete() {
        try {
            return getPosition() + " (" + getRepresentation() + ")";
        } catch (Exception e) {
            return getTileID();
        }
    }
}
