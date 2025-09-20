package ti4.service.draft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Data;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Tile;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;
import ti4.model.WormholeModel;
import ti4.service.milty.MiltyDraftTile;
import ti4.service.milty.TierList;

@Data
public class DraftTileManager {

    private static final Map<String, MiltyDraftTile> tiles = new HashMap<>();

    private final List<MiltyDraftTile> all = new ArrayList<>();
    private final List<MiltyDraftTile> blue = new ArrayList<>();
    private final List<MiltyDraftTile> red = new ArrayList<>();

    public void addDraftTile(MiltyDraftTile draftTile) {
        TierList draftTileTier = draftTile.getTierList();
        switch (draftTileTier) {
            case high, mid, low -> blue.add(draftTile);
            case red, anomaly -> red.add(draftTile);
        }
        all.add(draftTile);
    }

    public List<MiltyDraftTile> getBlue() {
        return new ArrayList<>(blue);
    }

    public List<MiltyDraftTile> getRed() {
        return new ArrayList<>(red);
    }

    public List<MiltyDraftTile> filterAll(Predicate<MiltyDraftTile> predicate) {
        return all.stream().filter(predicate).collect(Collectors.toList());
    }

    public void clear() {
        all.clear();
        blue.clear();
        red.clear();
    }

    public void reset(Game game) {
        clear();
        addAllDraftTiles(getGameSources(game));
    }

    public void addAllDraftTiles(List<ComponentSource> sources) {
        List<TileModel> allTiles = new ArrayList<>(TileHelper.getAllTileModels());
        for (TileModel tileModel : allTiles) {
            if (isNotDraftable(tileModel)) continue;
            if (!sources.contains(tileModel.getSource())) continue;
            if (tileModel.getTileBack() == TileBack.GREEN || tileModel.isHyperlane()) continue;

            MiltyDraftTile draftTile = getDraftTileFromModel(tileModel);
            addDraftTile(draftTile);
        }
    }

    public static void resetForGame(Game game) {
        DraftTileManager tileManager = game.getDraftTileManager();
        tileManager.reset(game);
    }

    public static void addAllDraftTiles(Game game) {
        List<ComponentSource> sources = getGameSources(game);
        DraftTileManager tileManager = game.getDraftTileManager();
        tileManager.addAllDraftTiles(sources);
    }

    public static MiltyDraftTile findTile(String tileId) {
        MiltyDraftTile result = tiles.get(tileId);
        if (result != null) {
            return result;
        }

        TileModel tileRequested = TileHelper.getTileById(tileId);
        if (tileRequested == null) {
            throw new IllegalArgumentException("No such tile with ID: " + tileId);
        }

        result = getDraftTileFromModel(tileRequested);
        return result;
    }

    private static List<ComponentSource> getGameSources(Game game) {
        List<ComponentSource> sources = new ArrayList<>(Arrays.asList(
                ComponentSource.base,
                ComponentSource.codex1,
                ComponentSource.codex2,
                ComponentSource.codex3,
                ComponentSource.codex4,
                ComponentSource.pok));
        if (game.isDiscordantStarsMode() || game.isUnchartedSpaceStuff()) {
            sources.add(ComponentSource.ds);
            sources.add(ComponentSource.uncharted_space);
        }
        return sources;
    }

    private static boolean isNotDraftable(TileModel tileModel) {
        TileModel.TileBack back = tileModel.getTileBack();
        if (back != TileBack.RED && back != TileBack.BLUE) {
            return true;
        }

        String id = tileModel.getId().toLowerCase();
        String path =
                tileModel.getImagePath() == null ? "" : tileModel.getImagePath().toLowerCase();
        List<String> disallowedTerms = List.of(
                "corner", "lane", "mecatol", "blank", "border", "fow", "anomaly", "deltawh", "seed", "mr", "mallice",
                "ethan", "prison", "kwon", "home", "hs", "red", "blue", "green", "gray", "gate", "setup");
        return disallowedTerms.stream().anyMatch(term -> id.contains(term) || path.contains(term));
    }

    private static MiltyDraftTile getDraftTileFromModel(TileModel tileModel) {
        String tileID = tileModel.getId();
        if (tiles.containsKey(tileID)) return tiles.get(tileID);

        Set<WormholeModel.Wormhole> wormholes = tileModel.getWormholes();
        MiltyDraftTile draftTile = new MiltyDraftTile();
        if (wormholes != null) {
            for (WormholeModel.Wormhole wormhole : wormholes) {
                if (wormhole == WormholeModel.Wormhole.ALPHA) {
                    draftTile.setHasAlphaWH(true);
                } else if (wormhole == WormholeModel.Wormhole.BETA) {
                    draftTile.setHasBetaWH(true);
                } else {
                    draftTile.setHasOtherWH(true);
                }
            }
        }

        Tile tile = new Tile(tileID, "none");
        draftTile.setTile(tile);

        for (Planet planet : tile.getPlanetUnitHolders()) {
            draftTile.addPlanet(planet);
        }

        if (tile.isAnomaly()) {
            draftTile.setTierList(TierList.anomaly);
        } else if (tile.getPlanetUnitHolders().isEmpty()) {
            draftTile.setTierList(TierList.red);
        } else {
            draftTile.setTierList(TierList.high);
        }

        tiles.put(tileID, draftTile);
        return draftTile;
    }
}
