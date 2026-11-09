package ti4.image.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.fow.FogViewer;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.map.HyperlaneStyleService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

// The hyperlane-bridges-fog testbed map: destroyers in 302, 304 and 105; one-way chain 201 -> Wellon 101 -> 000 -> 104;
// two-way lane 203 <-> 102 <-> 000 <-> 105. Bridges on, so Wellon and 102 are passed through and never seen.
class FogBridgeVisibilityTest extends BaseTi4Test {

    private Game game;
    private Player empyrean;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setFowMode(true);
        empyrean = game.addPlayer("111", "empyrean");
        empyrean.setFaction(game, "empyrean");
        empyrean.setColor("blue");
        for (String[] system :
                new String[][] {{"302", "20"}, {"304", "21"}, {"105", "23"}, {"101", "19"}, {"102", "24"}, {"104", "22"}
                }) {
            game.setTile(new Tile(system[1], system[0]));
        }
        lane("201", 0, 3, true);
        lane("000", 0, 3, true);
        twoWay("000", 1, 4);
        twoWay("203", 4, 1);
        lane("208", 1, 4, true);
        HyperlaneStyleService.setShowsBridges(game, true);
        for (String position : new String[] {"302", "304", "105"}) {
            game.getTileByPosition(position)
                    .addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, empyrean.getColorID()), 1);
        }
    }

    @Test
    void empyreanSeesTheHyperlanesNextToItsShipsButNotTheBridgedSystems() {
        assertThat(FoWHelper.getTilePositionsToShow(game, empyrean))
                .contains("201", "203", "000", "208", "104")
                .doesNotContain("101", "102");
    }

    // Never-seen bridged systems are drawn as hyperlane tiles with a ping, so the framed map must keep them.
    @Test
    void framedMapKeepsTheNeverSeenBridgedSystems() {
        for (Tile tile : game.getTileMap().values()) {
            tile.setTileFog(
                    empyrean, !FoWHelper.getTilePositionsToShow(game, empyrean).contains(tile.getPosition()));
        }
        FogView fog = FogView.of(game, new FogViewer(game, true, empyrean));
        assertThat(fog.seenPositions()).contains("101", "102", "201");
    }

    // Hide unseen hyperlanes: 102's lanes (203 and 000's NE-SW lane) are both on Empyrean's vision path, so the
    // compact overview keeps 102; Wellon's 201 lane is not (302 only sees 201 from the side), so Wellon stays out.
    @Test
    void compactOverviewKeepsBridgesWhoseLanesAreSeen() {
        game.setFowOption(FOWOption.HIDE_UNSEEN_HYPERLANES, true);
        for (Tile tile : game.getTileMap().values()) {
            tile.setTileFog(
                    empyrean, !FoWHelper.getTilePositionsToShow(game, empyrean).contains(tile.getPosition()));
        }
        assertThat(CompactOverviewGenerator.knownTiles(game, empyrean))
                .containsKeys("102", "201", "203", "000")
                .doesNotContainKey("101");
    }

    // Hide unexplored map removes never-seen systems, but a bridged one stays: visible lanes must not lead into
    // nothing.
    @Test
    void hideUnexploredMapStillShowsTheBridgedSystem() {
        game.setFowOption(FOWOption.HIDE_MAP, true);
        for (Tile tile : game.getTileMap().values()) {
            tile.setTileFog(
                    empyrean, !FoWHelper.getTilePositionsToShow(game, empyrean).contains(tile.getPosition()));
        }
        FogView fog = FogView.of(game, new FogViewer(game, true, empyrean));
        assertThat(fog.tiles()).containsKey("102");
        assertThat(fog.seenPositions()).contains("102");
        assertThat(CompactOverviewGenerator.knownTiles(game, empyrean)).containsKey("102");
    }

    private void lane(String position, int from, int to, boolean add) {
        int[][] lanes = game.getCustomHyperlaneData().containsKey(position)
                ? CustomHyperlaneService.parseMatrix(
                        game.getCustomHyperlaneData().get(position))
                : new int[6][6];
        lanes[from][to] = add ? 1 : 0;
        game.setTile(new Tile("hl", position));
        game.getCustomHyperlaneData().put(position, CustomHyperlaneService.formatMatrix(lanes));
    }

    private void twoWay(String position, int a, int b) {
        lane(position, a, b, true);
        lane(position, b, a, true);
    }
}
