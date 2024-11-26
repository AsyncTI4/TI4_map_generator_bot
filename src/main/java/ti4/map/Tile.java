package ti4.map;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.message.BotLogger;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.model.WormholeModel;

public class Tile {
    private final String tileID;
    private String position;
    private final Map<String, UnitHolder> unitHolders = new LinkedHashMap<>();

    @JsonIgnore
    private final HashMap<Player, Boolean> fog = new LinkedHashMap<>();
    @JsonIgnore
    private final HashMap<Player, String> fogLabel = new LinkedHashMap<>();

    public Tile(@JsonProperty("tileID") String tileID, @JsonProperty("position") String position) {
        this.tileID = tileID;
        this.position = position != null ? position.toLowerCase() : null;
        initPlanetsAndSpace(tileID);
    }

    public Tile(String tileID, String position, Player player, Boolean fog_, String fogLabel_) {
        this.tileID = tileID;
        this.position = position != null ? position.toLowerCase() : null;
        if (player != null) {
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
        Map<String, Point> tilePlanetPositions = PositionMapper.getTilePlanetPositions(tileID);

        if (tilePlanetPositions != null)
            tilePlanetPositions
                .forEach((planetName, position) -> unitHolders.put(planetName, new Planet(planetName, position)));
    }

    @Nullable
    public String getCCPath(String ccID) {
        return Mapper.getCCPath(ccID);
    }

    @Nullable
    public String getAttachmentPath(String tokenID) {
        return ResourceHelper.getInstance().getAttachmentFile(tokenID);
    }

    @Nullable
    public String getTokenPath(String tokenID) {
        return Mapper.getTokenPath(tokenID);
    }

    public boolean isSpaceHolderValid(String spaceHolder) {
        return unitHolders.get(spaceHolder) != null;
    }

    public void addUnit(String spaceHolder, UnitKey unitID, Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.addUnit(unitID, count);
        }
    }

    public void addUnitDamage(String spaceHolder, UnitKey unitID, @Nullable Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder == null || count == null) {
            return;
        }
        Map<UnitKey, Integer> units = unitHolder.getUnits();
        Integer unitCount = units.get(unitID);
        if (unitCount == null) {
            return;
        }
        if (unitCount < count) {
            count = unitCount;
        }
        unitHolder.addDamagedUnit(unitID, count);
    }

    public void addCC(String ccID) {
        UnitHolder unitHolder = unitHolders.get(Constants.SPACE);
        if (unitHolder != null) {
            unitHolder.addCC(ccID);
        }
    }

    public boolean hasPlayerCC(Player player) {
        String color = player.getColor();
        String ccID = Mapper.getCCID(color);
        return hasCC(ccID);
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

    public boolean removeToken(String tokenID, String spaceHolder) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            return unitHolder.removeToken(tokenID);
        }
        return false;
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

    public void removeUnit(String spaceHolder, UnitKey unitID, Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null) {
            unitHolder.removeUnit(unitID, count);
        }
    }

    public void removeUnitDamage(String spaceHolder, UnitKey unitID, @Nullable Integer count) {
        UnitHolder unitHolder = unitHolders.get(spaceHolder);
        if (unitHolder != null && count != null) {
            unitHolder.removeDamagedUnit(unitID, count);
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

    public void addUnit(String spaceHolder, UnitKey unitID, String count) {
        try {
            int unitCount = Integer.parseInt(count);
            addUnit(spaceHolder, unitID, unitCount);
        } catch (Exception e) {
            BotLogger.log("Could not parse unit count", e);
        }
    }

    public void addUnitDamage(String spaceHolder, UnitKey unitID, String count) {
        try {
            int unitCount = Integer.parseInt(count);
            addUnitDamage(spaceHolder, unitID, unitCount);
        } catch (Exception e) {
            BotLogger.log("Could not parse unit count", e);
        }
    }

    @JsonIgnore
    public List<Boolean> getHyperlaneData(Integer sourceDirection) {
        List<List<Boolean>> fullHyperlaneData = Mapper.getHyperlaneData(tileID);
        if (fullHyperlaneData.isEmpty()) {
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

    @JsonIgnore
    public String getTilePath() {
        String tileName = Mapper.getTileID(tileID);
        if (("44".equals(tileID) || ("45".equals(tileID)))
            && (ThreadLocalRandom.current().nextInt(Constants.EYE_CHANCE) == 0)) {
            tileName = "S15_Cucumber.png";
        }
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            BotLogger.log("Could not find tile: " + tileID);
        }
        return tilePath;
    }

    public Map<Player, Boolean> getFog() {
        return new HashMap<>(fog);
    }

    public Map<Player, String> getFogLabel() {
        return new HashMap<>(fogLabel);
    }

    public boolean hasFog(Player player) {
        if (player == null) return true;
        Boolean hasFog = fog.get(player);

        Game game = player.getGame();
        if (game.isLightFogMode() && player.getFogTiles().containsKey(getPosition())) {
            return false;
        }
        // default all tiles to being foggy to prevent unintended info leaks
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

    @JsonIgnore
    public String getFowTilePath(Player player) {
        String fogTileColor = player == null ? "default" : player.getFogFilter();
        String fogTileColorSuffix = "_" + fogTileColor;
        String fowTileID = "fow" + fogTileColorSuffix;

        if ("82b".equals(tileID) || "51".equals(tileID)) { // mallice || creuss
            fowTileID = "fowb" + fogTileColorSuffix;
        }
        if ("82a".equals(tileID)) { // mallicelocked
            fowTileID = "fowc" + fogTileColorSuffix;
        }

        String tileName = Mapper.getTileID(fowTileID);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            BotLogger.log("Could not find tile: " + fowTileID);
        }
        return tilePath;
    }

    public Map<String, UnitHolder> getUnitHolders() {
        return unitHolders;
    }

    public List<Planet> getPlanetUnitHolders() {
        List<Planet> planets = new ArrayList<>();
        for (UnitHolder uH : unitHolders.values()) {
            if (uH instanceof Planet p && !p.getTokenList().contains(Constants.WORLD_DESTROYED_PNG)) {
                planets.add(p);
            }
        }
        return planets;
    }

    @JsonIgnore
    @Nullable
    public Planet getUnitHolderFromPlanet(String planetName) {
        for (Map.Entry<String, UnitHolder> unitHolderEntry : getUnitHolders().entrySet()) {
            if (unitHolderEntry.getValue() instanceof Planet p && unitHolderEntry.getKey().equals(planetName)) {
                return p;
            }
        }
        return null;
    }

    @JsonIgnore
    public UnitHolder getSpaceUnitHolder() {
        if (unitHolders.get("space") == null)
            return null;
        return unitHolders.get("space");
    }

    @JsonIgnore
    public String getRepresentation() {
        try {
            if (Mapper.getTileRepresentations().get(getTileID()) == null) {
                return getTileID() + "(" + getPosition() + ")";
            }
            return Mapper.getTileRepresentations().get(getTileID());
        } catch (Exception e) {
            // DO NOTHING
        }
        return null;
    }

    @JsonIgnore
    public String getRepresentationForButtons() {
        return getRepresentationForButtons(null, null);
    }

    @JsonIgnore
    public String getRepresentationForButtons(Game game, Player player) {
        try {
            if (game != null && game.isFowMode()) {
                if (player == null)
                    return getPosition();

                Set<String> tilesToShow = FoWHelper.getTilePositionsToShow(game, player);
                if (tilesToShow.contains(getPosition())) {
                    return getPosition() + " (" + getRepresentation() + ")";
                } else {
                    return getPosition();
                }
            } else {
                return getPosition() + " (" + getRepresentation() + ")";
            }

        } catch (Exception e) {
            return getTileID();
        }
    }

    @JsonIgnore
    public String getAutoCompleteName() {
        try {
            return getPosition() + " (" + getRepresentation() + ")";
        } catch (Exception e) {
            return getTileID();
        }
    }

    @JsonIgnore
    public List<String> getPlanetsWithSleeperTokens() {
        List<String> planetsWithSleepers = new ArrayList<>();
        for (UnitHolder unitHolder : getUnitHolders().values()) {
            if (unitHolder instanceof Planet planet) {
                if (planet.getTokenList().contains(Constants.TOKEN_SLEEPER_PNG)) {
                    planetsWithSleepers.add(planet.getName());
                }
            }
        }
        return planetsWithSleepers;
    }

    @JsonIgnore
    public TileModel getTileModel() {
        return TileHelper.getTileById(getTileID());
    }

    @JsonIgnore
    public boolean isAsteroidField() {
        return getTileModel().isAsteroidField();
    }

    @JsonIgnore
    public boolean isSupernova() {
        return getTileModel().isSupernova();
    }

    @JsonIgnore
    public boolean isNebula() {
        for (UnitHolder unitHolder : getUnitHolders().values()) {
            if (CollectionUtils.containsAny(unitHolder.getTokenList(), "token_ds_wound.png")) {
                return true;
            }
        }
        return getTileModel().isNebula();
    }

    @JsonIgnore
    public boolean isGravityRift() {
        for (UnitHolder unitHolder : getUnitHolders().values()) {
            if (CollectionUtils.containsAny(unitHolder.getTokenList(), "token_ds_wound.png")) {
                return true;
            }
        }
        return getTileModel().isGravityRift() || hasCabalSpaceDockOrGravRiftToken();
    }

    @JsonIgnore
    public boolean isGravityRift(Game game) {
        for (UnitHolder unitHolder : getUnitHolders().values()) {
            if (CollectionUtils.containsAny(unitHolder.getTokenList(), "token_ds_wound.png")) {
                return true;
            }
        }
        return getTileModel().isGravityRift() || hasCabalSpaceDockOrGravRiftToken(game);
    }

    @JsonIgnore
    public Set<WormholeModel.Wormhole> getWormholes() {
        Set<WormholeModel.Wormhole> whs = new HashSet<>();
        if (getTileModel().getWormholes() != null)
            whs.addAll(getTileModel().getWormholes());
        for (String token : getSpaceUnitHolder().getTokenList()) {
            if (token.contains("alpha")) whs.add(WormholeModel.Wormhole.ALPHA);
            if (token.contains("beta")) whs.add(WormholeModel.Wormhole.BETA);
            if (token.contains("gamma")) whs.add(WormholeModel.Wormhole.GAMMA);
        }
        return whs;
    }

    @JsonIgnore
    public int getWormholeCount() {
        int whs = 0;
        if (getTileModel().getWormholes() != null)
            whs += getTileModel().getWormholes().size();
        for (String token : getSpaceUnitHolder().getTokenList()) {
            if (token.contains("alpha")) whs++;
            if (token.contains("beta")) whs++;
            if (token.contains("gamma")) whs++;
        }
        return whs;
    }

    @JsonIgnore
    public boolean hasCabalSpaceDockOrGravRiftToken() {
        return hasCabalSpaceDockOrGravRiftToken(null);
    }

    @JsonIgnore
    public boolean hasCabalSpaceDockOrGravRiftToken(Game game) {
        for (UnitHolder unitHolder : getUnitHolders().values()) {
            Set<String> tokenList = unitHolder.getTokenList();
            if (CollectionUtils.containsAny(tokenList, "token_gravityrift.png", "token_ds_wound.png")) {
                return true;
            }
            for (UnitKey unit : unitHolder.getUnits().keySet()) {
                if (unit.getUnitType() == UnitType.CabalSpacedock) {
                    return true;
                }
                if (unit.getUnitType() == UnitType.Spacedock && game != null) {
                    Player player = game.getPlayerFromColorOrFaction(unit.getColor());
                    if (player != null && player.getUnitFromUnitKey(unit).getId().contains("cabal_spacedock")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isAnomaly() {
        if (isAsteroidField() || isSupernova() || isNebula() || isGravityRift()) {
            return true;
        }
        for (UnitHolder unitHolder : getUnitHolders().values()) {
            if (CollectionUtils.containsAny(unitHolder.getTokenList(), "token_ds_wound.png", "token_ds_sigil.png",
                "token_anomalydummy.png")) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isMecatol() {
        if (Constants.MECATOL_SYSTEMS.contains(getTileID())) {
            return true;
        }
        return CollectionUtils.containsAny(unitHolders.keySet(), Constants.MECATOLS);
    }

    @JsonIgnore
    public boolean isEdgeOfBoard(Game game) {
        List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(position);
        if (directlyAdjacentTiles == null || directlyAdjacentTiles.size() != 6) {
            // adjacency file for this tile is not filled in
            return true;
        }
        // for each adjacent tile...
        for (int i = 0; i < 6; i++) {
            String position_ = directlyAdjacentTiles.get(i);
            if (game.getTileByPosition(position_) == null) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean isAnomaly(Game game) {
        if (isAsteroidField() || isSupernova() || isNebula() || isGravityRift(game)) {
            return true;
        }
        for (UnitHolder unitHolder : getUnitHolders().values()) {
            if (CollectionUtils.containsAny(unitHolder.getTokenList(), "token_ds_wound.png", "token_ds_sigil.png", "token_anomalydummy.png")) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public boolean containsPlayersUnits(Player p) {
        return getUnitHolders().values().stream()
            .flatMap(uh -> uh.getUnits().entrySet().stream())
            .anyMatch(e -> e.getValue() > 0 && p.unitBelongsToPlayer(e.getKey()));
    }

    @JsonIgnore
    public boolean containsPlayersUnitsWithModelCondition(Player p, Predicate<? super UnitModel> condition) {
        return getUnitHolders().values().stream()
            .flatMap(uh -> uh.getUnits().entrySet().stream())
            .filter(e -> e.getValue() > 0 && p.unitBelongsToPlayer(e.getKey()))
            .map(Map.Entry::getKey)
            .map(p::getUnitFromUnitKey)
            .filter(Objects::nonNull)
            .anyMatch(condition);
    }

    @JsonIgnore
    public boolean containsPlayersUnitsWithKeyCondition(Player p, Predicate<? super UnitKey> condition) {
        return getUnitHolders().values().stream()
            .flatMap(uh -> uh.getUnits().entrySet().stream())
            .filter(e -> e.getValue() > 0 && p.unitBelongsToPlayer(e.getKey()))
            .map(Map.Entry::getKey)
            .filter(Objects::nonNull)
            .anyMatch(condition);
    }

    @JsonIgnore
    public boolean search(String searchString) {
        return getTileID().contains(searchString) ||
            getPosition().contains(searchString) ||
            getTileModel().search(searchString);
    }

    public boolean isHomeSystem(Game game) {
        for (Player p : game.getRealAndEliminatedPlayers()) {
            Tile home = p.getHomeSystemTile();
            if (home != null && home.getTileID().equals(this.getTileID()))
                return true;
        }
        return false;
    }

    @JsonIgnore
    public boolean isHomeSystem() {
        if ("0g".equalsIgnoreCase(tileID)) {
            return true;
        }

        //TileModel model = getTileModel();
        // if (model != null) {
        //     if (StringUtils.isNotBlank(model.getTileBack())) {
        //         // if the tile back is defined, that is the source of truth
        //         return "green".equals(model.getTileBack());
        //     }
        //     for (String p : model.getPlanets()) {
        //         PlanetModel planet = Mapper.getPlanet(p);
        //         if (StringUtils.isNotBlank(planet.getFactionHomeworld())) {
        //             return true;
        //         }
        //     }
        //     return false;
        // }
        for (UnitHolder unitHolder : unitHolders.values()) {
            if (unitHolder instanceof Planet planetHolder) {
                boolean oneOfThree = (unitHolder.getTokenList() != null && unitHolder.getTokenList().contains("attachment_threetraits.png")) || (planetHolder.getOriginalPlanetType() != null
                    && ("industrial".equalsIgnoreCase(planetHolder.getOriginalPlanetType())
                        || "cultural".equalsIgnoreCase(planetHolder.getOriginalPlanetType())
                        || "hazardous".equalsIgnoreCase(planetHolder.getOriginalPlanetType())));

                if (!Constants.MECATOLS.contains(planetHolder.getName()) && !oneOfThree) {
                    return true;
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public int getFleetSupplyBonusForPlayer(final Player player) {
        return getUnitHolders().values().stream()
            .flatMap(unitHolder -> unitHolder.getUnits().entrySet().stream())
            .filter(entry -> entry.getValue() > 0 && player.unitBelongsToPlayer(entry.getKey()))
            .map(Map.Entry::getKey)
            .map(player::getUnitFromUnitKey)
            .filter(Objects::nonNull)
            .mapToInt(UnitModel::getFleetSupplyBonus)
            .sum();
    }

    public static Predicate<Tile> tileHasPlayerShips(Player player) {
        return tile -> tile.containsPlayersUnitsWithModelCondition(player, UnitModel::getIsShip);
    }

    public static Predicate<Tile> tileHasPlayerUnits(Player player) {
        return tile -> tile.containsPlayersUnits(player);
    }

    public String getHexTileSummary() {
        // TILE +-X +-Y SPACE ; PLANET1 ; PLANET2 ;
        StringBuilder sb = new StringBuilder();
        sb.append(getTileID());
        sb.append(AliasHandler.resolveTTPGPosition(getPosition()));
        return sb.toString();
    }
}
