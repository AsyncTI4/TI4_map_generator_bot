package ti4.helpers.async;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import ti4.image.TileHelper;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.model.TileModel.TileBack;

// Jazz's Interactive Map Builder
public class JimboConst {
    // Main Page
    public static final String mainPage = "jimbo_mainPage";
    public static final String exit = "jimbo_exit";

    // Tiles: red/blue/green/hyperlane/draft/{other?}
    public static final String tileAction = "jimbo_tileAction";
    public static final String tileAdd = "jimbo_tileAdd";
    public static final String tileRemove = "jimbo_tileRemove";
    public static final String tileMove = "jimbo_tileMove";

    // Features : border anomalies and tokens / attachments
    public static final String featureAction = "jimbo_featureAction";
    public static final String tokenAdd = "jimbo_tokenAdd";
    public static final String tokenRemove = "jimbo_tokenRemove";
    public static final String borderAdd = "jimbo_borderAdd";
    public static final String borderRemove = "jimbo_borderRemove";

    // Transformations: Rotate, Translate
    public static final String transformAction = "jimbo_transformAction";
    public static final String transformRotate = "jimbo_transformRotate_";
    public static final String transformTranslate = "jimbo_transformTranslate_";

    // Meta Actions: Add symmetry (and other settings TBD)
    public static final String metaAction = "jimbo_metaAction";
    public static final String metaSymmetryAdd = "jimbo_metaSymmetryAdd";
    public static final String metaSymmetryRemove = "jimbo_metaSymmetryRemove";

    // Tile separation
    public static List<TileModel> blueTiles;
    public static List<TileModel> redTiles;
    public static List<TileModel> greenTiles;
    public static List<TileModel> hyperlaneTiles;
    public static Map<Integer, List<TileModel>> hyperlanesByRotation;
    public static List<TileModel> draftTiles;
    public static Map<Integer, List<TileModel>> draftTilesByNumber;
    public static List<TileModel> otherTiles;

    public static String o() {
        List<String> candidates = new ArrayList<>();
        candidates.addAll(List.of(
                "oasis",
                "ocean",
                "octagon",
                "office",
                "object",
                "octopus",
                "ogre",
                "omelette",
                "ode",
                "operator",
                "operators",
                "opinions",
                "opportunity",
                "option",
                "oath",
                "oven",
                "origin",
                "ope"));
        candidates.addAll(List.of(
                "obsession",
                "observer",
                "observatory",
                "oddity",
                "odyssey",
                "onion",
                "ood",
                "offer",
                "offshoot",
                "ohana",
                "old-timer",
                "ohm",
                "omg",
                "omnipotence",
                "ordinian",
                "olradin",
                "obelisk"));
        candidates.addAll(List.of(
                "onomatopoeia",
                "onyx",
                "oodles",
                "opener",
                "opening",
                "opera",
                "optimizer",
                "opus",
                "oracle",
                "orange",
                "occulus",
                "oration",
                "orchestra",
                "orchid",
                "order",
                "obsidian",
                "olergodt"));
        candidates.addAll(List.of(
                "organism",
                "organization",
                "organizer",
                "orientation",
                "origami",
                "ornament",
                "ornithologist",
                "orthodontist",
                "osmosis",
                "ostrich",
                "outback",
                "outfit",
                "Okke",
                "orchard",
                "ospha"));
        candidates.addAll(List.of(
                "output",
                "outside",
                "oval",
                "ovation",
                "overgrowth",
                "overhaul",
                "override",
                "overseer",
                "overture",
                "owl",
                "oxymoron",
                "oxytocin",
                "oyster",
                "ozone",
                "omega",
                "Ogdun",
                "Oy-Oy-Oy"));
        candidates.addAll(List.of("o-word", "o-backronym"));
        Collections.shuffle(candidates);
        return StringUtils.capitalize(candidates.getFirst());
    }

    public static void setupTileStuff() {
        if (blueTiles != null) return;

        Function<TileModel, Integer> sourceOrder = t -> t.getSource().ordinal();
        Comparator<TileModel> comp = Comparator.comparing(sourceOrder).thenComparing(TileModel::getAlias);
        // sort by source, then by alias
        List<TileModel> allTilesSorted =
                TileHelper.getAllTileModels().stream().sorted(comp).toList();

        blueTiles = allTilesSorted.stream()
                .filter(t -> "0b".equals(t.getAlias()) || t.getTileBack() == TileBack.BLUE)
                .toList();
        redTiles = allTilesSorted.stream()
                .filter(t -> "0r".equals(t.getAlias()) || t.getTileBack() == TileBack.RED)
                .toList();
        greenTiles = allTilesSorted.stream()
                .filter(t -> "0g".equals(t.getAlias()) || t.getTileBack() == TileBack.GREEN)
                .toList();
        hyperlaneTiles = allTilesSorted.stream()
                .filter(t -> t.getName() != null && "hyperlane".equalsIgnoreCase(t.getName()))
                .toList();
        draftTiles = allTilesSorted.stream().filter(TileHelper::isDraftTile).toList();
        otherTiles = new ArrayList<>();
        List<TileModel> ignore = Stream.of(blueTiles, redTiles, greenTiles, hyperlaneTiles, draftTiles)
                .flatMap(Collection::stream)
                .toList();
        allTilesSorted.stream()
                .filter(t -> !ignore.contains(t))
                .filter(t -> t.getSource() != ComponentSource.fow)
                .forEach(otherTiles::add);

        setupHyperlanes();
        setupDraftTiles();
    }

    // Hyperlane Data is formatted such that each index has all the same hyperlanes in order and also later rotations
    // get filled in, e.g.
    // 0: 83a   , 83b   , 84a   , 84b   , ..., hl_spaghet_0, hl_roundabout3_0
    // 1: 83a60 , 83b60 , 84a60 , 84b60 , ..., hl_spaghet_0, hl_roundabout3_1
    // 2: 83a120, 83b120, 84a120, 84b120, ..., hl_spaghet_0, hl_roundabout3_0
    private static void setupHyperlanes() {
        Set<String> baseStringOrder = new LinkedHashSet<>();
        Map<Integer, Map<String, TileModel>> tilesByRotation = new HashMap<>();
        for (int i = 0; i < 6; i++) tilesByRotation.put(i, new HashMap<>());

        for (TileModel hl : hyperlaneTiles) {
            String baseTile = hl.getId();
            Matcher matcher = Pattern.compile("\\d+$").matcher(baseTile);
            if (matcher.find()) baseTile = matcher.replaceFirst("");

            String rotationStr = hl.getId().replace(baseTile, "");
            int rotation =
                    switch (rotationStr) {
                        case "1", "60" -> 1;
                        case "2", "120" -> 2;
                        case "3", "180" -> 3;
                        case "4", "240" -> 4;
                        case "5", "300" -> 5;
                        default -> 0;
                    };
            baseStringOrder.add(baseTile);
            tilesByRotation.get(rotation).put(baseTile, hl);
        }

        // Build out hyperlane data by index
        for (String base : baseStringOrder) {
            int dupeOffset = 0;
            for (int i = 0; i < 6; i++) {
                if (tilesByRotation.get(i).containsKey(base)) dupeOffset = i + 1;
            }
            for (int i = 0; i < 6; i++) {
                for (int j = i + dupeOffset; j < 6; j += dupeOffset) {
                    tilesByRotation.get(j).put(base, tilesByRotation.get(i).get(base));
                }
            }
        }

        // Store the data for convenient use later
        hyperlanesByRotation = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            hyperlanesByRotation.put(i, new ArrayList<>());
            for (String base : baseStringOrder) {
                hyperlanesByRotation.get(i).add(tilesByRotation.get(i).get(base));
            }
        }
    }

    // Draft tile data is formatted similar to hyperlanes, except we don't need to special handle missing tiles
    // -1: redblank, blueblank, greenblank, ...
    //  0: red0    , blue0    , green0    , ...
    //  1: red1    , blue1    , green1    , ...
    private static void setupDraftTiles() {
        Set<String> baseStringOrder = new LinkedHashSet<>();
        Map<Integer, Map<String, TileModel>> tilesByNum = new HashMap<>();
        for (TileModel tile : draftTiles) {
            String color = tile.getId().replaceAll("(blank|\\d+)$", "");
            String indexStr = tile.getId().replace(color, "");
            int index;
            switch (indexStr) {
                case "blank" -> index = -1;
                default -> {
                    try {
                        index = Integer.parseInt(indexStr);
                    } catch (Exception e) {
                        index = -2;
                    }
                }
            }
            if (index < -1) continue;
            baseStringOrder.add(color);
            if (!tilesByNum.containsKey(index)) tilesByNum.put(index, new HashMap<>());
            tilesByNum.get(index).put(color, tile);
        }

        // Store the data for convenient use later
        draftTilesByNumber = new HashMap<>();
        for (Integer x : tilesByNum.keySet()) {
            draftTilesByNumber.put(x, new ArrayList<>());
            for (String base : baseStringOrder) {
                draftTilesByNumber.get(x).add(tilesByNum.get(x).get(base));
            }
        }
    }
}
