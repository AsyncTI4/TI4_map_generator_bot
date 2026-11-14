package ti4.service.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.MapPositionMapper;
import ti4.model.TileHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class RefractureLayoutRegistry {

    public static final String LAYOUT_EDGE_KEY = "refractureLayoutEdge";

    private static final Map<Integer, Layout> LAYOUTS = Map.of(
            1, new Layout(1, BoardEdge.TOP, List.of()),
            2,
                    layout(
                            2,
                            BoardEdge.TOP,
                            tile("fracture8", "frac3", units(unit("2 dd", "space")), List.of()),
                            tile("fracture9", "frac4", List.of(), List.of()),
                            tile(
                                    "fracture10",
                                    "frac5",
                                    units(
                                            unit("1 dn, 1 cr", "space"),
                                            unit("3 inf, 1 pds", "nile"),
                                            unit("1 inf", "anubis"),
                                            unit("1 inf", "sekhmet")),
                                    relics("nile", "anubis", "sekhmet")),
                            tile("fracture11", "frac14", List.of(), List.of()),
                            tile("fracture12", "frac6", List.of(), List.of()),
                            tile(
                                    "fracture13",
                                    "frac15",
                                    units(unit("3 dn", "space"), unit("1 inf, 2 pds", "matercalyxa")),
                                    relics("matercalyxa")),
                            tile("fracture14", "frac7", List.of(), List.of()),
                            tile(
                                    "fracture15",
                                    "frac16",
                                    units(unit("2 cr, 2 ff", "space"), unit("1 inf", "charon")),
                                    tokens(relic("charon"), charonToken())),
                            tile("fracture16", "frac17", List.of(), List.of())),
            3,
                    layout(
                            3,
                            BoardEdge.TOP,
                            tile(
                                    "fracture17",
                                    "frac3",
                                    units(
                                            unit("1 dn, 1 ff", "space"),
                                            unit("1 inf, 1 pds", "experiment"),
                                            unit("1 inf", "janus")),
                                    relics("experiment", "janus")),
                            tile("fracture19", "frac4", List.of(), List.of()),
                            tile("fracture21", "frac5", List.of(), List.of()),
                            tile("fracture24", "frac6", List.of(), List.of()),
                            tile(
                                    "fracture25",
                                    "frac7",
                                    units(unit("1 ws, 1 dd", "space"), unit("1 mf, 2 inf", "lekythoi")),
                                    relics("lekythoi")),
                            tile("fracture11", "frac11", List.of(), List.of()),
                            tile("fracture18", "frac12", units(unit("7 inf", "fenrir")), relics("fenrir")),
                            tile("fracture20", "frac13", List.of(), List.of()),
                            tile(
                                    "fracture23",
                                    "frac14",
                                    units(unit("1 cv, 3 ff", "space"), unit("1 mf", "nidhogg")),
                                    relics("nidhogg")),
                            tile("fracture22", "frac15", List.of(), List.of())),
            4,
                    layout(
                            4,
                            BoardEdge.TOP,
                            tile("fracture3", "frac3", List.of(), List.of()),
                            tile(
                                    "fracture4",
                                    "frac4",
                                    units(unit("2 dn, 1 dd", "space"), unit("3 inf", "styx")),
                                    relics("styx")),
                            tile("fracture5", "frac5", List.of(), List.of()),
                            tile("fracture27", "frac6", List.of(), List.of()),
                            tile("fracture8", "frac13", units(unit("2 dd", "space")), List.of()),
                            tile("fracture26", "frac14", units(unit("1 fs, 2 ff", "space")), relics("ptomastation")),
                            tile("fracture28", "frac15", List.of(), List.of()),
                            tile("fracture9", "frac28", List.of(), List.of()),
                            tile(
                                    "fracture10",
                                    "frac29",
                                    units(
                                            unit("1 cr, 1 dn", "space"),
                                            unit("3 inf, 1 pds", "nile"),
                                            unit("1 inf", "anubis"),
                                            unit("1 inf", "sekhmet")),
                                    relics("nile", "anubis", "sekhmet"))),
            5,
                    layout(
                            5,
                            BoardEdge.TOP,
                            tile("fracture12", "frac2", List.of(), List.of()),
                            tile(
                                    "fracture13",
                                    "frac3",
                                    units(unit("3 dn", "space"), unit("2 pds, 1 inf", "matercalyxa")),
                                    relics("matercalyxa")),
                            tile("fracture19", "frac4", List.of(), List.of()),
                            tile("fracture21", "frac5", List.of(), List.of()),
                            tile(
                                    "fracture7",
                                    "frac11",
                                    units(
                                            unit("1 cv, 4 ff", "space"),
                                            unit("1 inf", "lethe"),
                                            unit("1 inf", "phlegethon")),
                                    relics("lethe", "phlegethon")),
                            tile("fracture18", "frac12", units(unit("7 inf", "fenrir")), relics("fenrir")),
                            tile("fracture20", "frac13", List.of(), List.of()),
                            tile("fracture6", "frac24", List.of(), List.of())),
            6,
                    layout(
                            6,
                            BoardEdge.LOWER_RIGHT,
                            tile(
                                    "fracture23",
                                    "frac1",
                                    units(unit("1 cv, 3 ff", "space"), unit("1 mf", "nidhogg")),
                                    relics("nidhogg")),
                            tile("fracture24", "frac2", List.of(), List.of()),
                            tile("fracture26", "frac3", units(unit("1 fs, 2 ff", "space")), relics("ptomastation")),
                            tile("fracture27", "frac4", List.of(), List.of()),
                            tile(
                                    "fracture29",
                                    "frac5",
                                    units(unit("1 ff", "space"), unit("2 mf", "maahes")),
                                    relics("maahes")),
                            tile("fracture22", "frac25", List.of(), List.of()),
                            tile(
                                    "fracture25",
                                    "frac10",
                                    units(unit("1 ws, 1 dd", "space"), unit("1 mf, 2 inf", "lekythoi")),
                                    relics("lekythoi")),
                            tile("fracture28", "frac11", List.of(), List.of()),
                            tile("fracture31", "frac12", List.of(), List.of()),
                            tile("fracture30", "frac13", List.of(), List.of())),
            7,
                    layout(
                            7,
                            BoardEdge.TOP,
                            tile(
                                    "fracture1",
                                    "frac2",
                                    units(unit("2 cr", "space"), unit("2 inf", "cocytus")),
                                    relics("cocytus")),
                            tile("fracture2", "frac3", List.of(), List.of()),
                            tile(
                                    "fracture29",
                                    "frac4",
                                    units(unit("1 ff", "space"), unit("2 mf", "maahes")),
                                    relics("maahes")),
                            tile("fracture30", "frac5", List.of(), List.of()),
                            tile("fracture14", "frac6", List.of(), List.of()),
                            tile("fracture16", "frac7", List.of(), List.of()),
                            tile(
                                    "fracture17",
                                    "frac13",
                                    units(
                                            unit("1 dn, 1 ff", "space"),
                                            unit("1 inf, 1 pds", "experiment"),
                                            unit("1 inf", "janus")),
                                    relics("experiment", "janus")),
                            tile("fracture31", "frac14", List.of(), List.of()),
                            tile(
                                    "fracture15",
                                    "frac15",
                                    units(unit("2 cr, 2 ff", "space"), unit("1 inf", "charon")),
                                    tokens(relic("charon"), charonToken()))),
            8,
                    layout(
                            8,
                            BoardEdge.LOWER_LEFT,
                            tile("fracture37", "frac1", List.of(), List.of()),
                            tile(
                                    "fracture36",
                                    "frac2",
                                    units(
                                            unit("2 cr, 1 dd", "space"),
                                            unit("2 inf", "izana-mi"),
                                            unit("2 inf", "izana-gi")),
                                    relics("izana-mi", "izana-gi")),
                            tile(
                                    "fracture35",
                                    "frac3",
                                    units(unit("5 ff", "space"), unit("2 inf", "makara")),
                                    relics("makara")),
                            tile(
                                    "fracture13",
                                    "frac4",
                                    units(unit("3 dn", "space"), unit("2 pds, 1 inf", "matercalyxa")),
                                    relics("matercalyxa")),
                            tile(
                                    "fracture34",
                                    "frac5",
                                    units(unit("6 ff", "space"), unit("1 inf", "pollux")),
                                    relics("pollux")),
                            tile("fracture33", "frac6", List.of(), List.of()),
                            tile(
                                    "fracture32",
                                    "frac7",
                                    units(unit("1 dd, 4 ff", "space"), unit("1 inf", "castor")),
                                    relics("castor")),
                            tile("fracture12", "frac12", List.of(), List.of())),
            9,
                    layout(
                            9,
                            BoardEdge.TOP,
                            tile("fracture27", "frac4", List.of(), List.of()),
                            tile(
                                    "fracture35",
                                    "frac5",
                                    units(unit("5 ff", "space"), unit("2 inf", "makara")),
                                    relics("makara")),
                            tile(
                                    "fracture36",
                                    "frac6",
                                    units(
                                            unit("2 cr, 1 dd", "space"),
                                            unit("2 inf", "izana-mi"),
                                            unit("2 inf", "izana-gi")),
                                    relics("izana-mi", "izana-gi")),
                            tile("fracture37", "frac7", List.of(), List.of()),
                            tile("fracture26", "frac12", units(unit("1 fs, 2 ff", "space")), relics("ptomastation")),
                            tile("fracture28", "frac13", List.of(), List.of()),
                            tile(
                                    "fracture17",
                                    "frac14",
                                    units(
                                            unit("1 dn, 1 ff", "space"),
                                            unit("1 inf, 1 pds", "experiment"),
                                            unit("1 inf", "janus")),
                                    relics("experiment", "janus"))),
            10,
                    layout(
                            10,
                            BoardEdge.TOP,
                            tile(
                                    "fracture34",
                                    "frac5",
                                    units(unit("6 ff", "space"), unit("1 inf", "pollux")),
                                    relics("pollux")),
                            tile("fracture33", "frac13", List.of(), List.of()),
                            tile(
                                    "fracture32",
                                    "frac27",
                                    units(unit("1 dd, 4 ff", "space"), unit("1 inf", "castor")),
                                    relics("castor")),
                            tile("fracture18", "frac14", units(unit("7 inf", "fenrir")), relics("fenrir")),
                            tile("fracture20", "frac15", List.of(), List.of()),
                            tile("fracture19", "frac6", List.of(), List.of()),
                            tile("fracture21", "frac7", List.of(), List.of()),
                            tile("fracture24", "frac28", List.of(), List.of()),
                            tile(
                                    "fracture25",
                                    "frac29",
                                    units(unit("1 ws, 1 dd", "space"), unit("1 mf, 2 inf", "lekythoi")),
                                    relics("lekythoi"))));

    private static Layout layout(int result, BoardEdge edge, TilePlacement... tiles) {
        return new Layout(result, edge, List.of(tiles));
    }

    private static TilePlacement tile(
            String tileId, String position, List<UnitPlacement> neutralUnits, List<TokenPlacement> tokens) {
        return new TilePlacement(tileId, position, 0, neutralUnits, tokens);
    }

    private static UnitPlacement unit(String unitList, String holder) {
        return new UnitPlacement(unitList, holder);
    }

    private static List<UnitPlacement> units(UnitPlacement... units) {
        return List.of(units);
    }

    private static TokenPlacement relic(String holder) {
        return new TokenPlacement("token_relictoken.png", holder);
    }

    private static TokenPlacement charonToken() {
        return new TokenPlacement("charontoken.png", "space");
    }

    private static List<TokenPlacement> relics(String... holders) {
        return java.util.Arrays.stream(holders)
                .map(RefractureLayoutRegistry::relic)
                .toList();
    }

    private static List<TokenPlacement> tokens(TokenPlacement... tokens) {
        return List.of(tokens);
    }

    public static boolean isConfigured(int result) {
        Layout layout = LAYOUTS.get(result);
        return layout != null && (result == 1 || layout.isValid());
    }

    public static boolean placeLayout(GenericInteractionCreateEvent event, Game game, int result) {
        Layout layout = LAYOUTS.get(result);
        if (layout == null || !layout.isValid() || !layout.canBePlacedIn(game)) return false;
        game.setStoredValue(LAYOUT_EDGE_KEY, layout.edge().name());

        Player neutral = game.getNeutral();
        String neutralColor = neutral.getColorID();
        List<Tile> tiles = new ArrayList<>();
        for (TilePlacement placement : layout.tiles()) {
            Tile tile = new Tile(placement.tileId(), layout.resolvePosition(placement.position()));
            for (TokenPlacement token : placement.tokens()) {
                tile.addToken(token.tokenId(), token.holder());
            }
            tiles.add(tile);
        }
        tiles.forEach(game::setTile);
        for (int index = 0; index < layout.tiles().size(); index++) {
            TilePlacement placement = layout.tiles().get(index);
            Tile tile = tiles.get(index);
            for (UnitPlacement units : placement.neutralUnits()) {
                AddUnitService.addUnits(event, tile, game, neutralColor, unitsWithHolder(units));
            }
        }
        FractureService.moveCornerHomeSystemsAwayFromRefracture(game);
        FractureService.moveMalliceAwayFromRefracture(game);
        return true;
    }

    private static String unitsWithHolder(UnitPlacement units) {
        return java.util.Arrays.stream(units.unitList().split(","))
                .map(String::trim)
                .map(unit -> unit + " " + units.holder())
                .collect(Collectors.joining(", "));
    }

    public record Layout(int result, BoardEdge edge, List<TilePlacement> tiles) {
        boolean isValid() {
            Set<String> tileIds = new java.util.HashSet<>();
            Set<String> positions = new java.util.HashSet<>();
            return !tiles.isEmpty()
                    && tiles.stream()
                            .allMatch(tile -> tileIds.add(tile.tileId())
                                    && positions.add(resolvePosition(tile.position()))
                                    && resolvePosition(tile.position()) != null
                                    && TileHelper.getTileById(tile.tileId()) != null
                                    && MapPositionMapper.isTilePositionValid(resolvePosition(tile.position()))
                                    && holdersAreValid(tile));
        }

        private static boolean holdersAreValid(TilePlacement placement) {
            Tile tile = new Tile(placement.tileId(), placement.position());
            return placement.neutralUnits().stream().allMatch(units -> tile.isSpaceHolderValid(units.holder()))
                    && placement.tokens().stream().allMatch(token -> tile.isSpaceHolderValid(token.holder()));
        }

        boolean canBePlacedIn(Game game) {
            return tiles.stream().noneMatch(tile -> game.getTileByPosition(resolvePosition(tile.position())) != null);
        }

        String resolvePosition(String position) {
            return position;
        }
    }

    public enum BoardEdge {
        TOP(0),
        UPPER_RIGHT(1),
        LOWER_RIGHT(2),
        BOTTOM(3),
        LOWER_LEFT(4),
        UPPER_LEFT(5);

        private final int rotation;

        BoardEdge(int rotation) {
            this.rotation = rotation;
        }

        int rotation() {
            return rotation;
        }
    }

    public record TilePlacement(
            String tileId,
            String position,
            int rotation,
            List<UnitPlacement> neutralUnits,
            List<TokenPlacement> tokens) {}

    public record UnitPlacement(String unitList, String holder) {}

    public record TokenPlacement(String tokenId, String holder) {}
}
