package ti4.service.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.service.map.PortalSlot.Flow;
import ti4.testUtils.BaseTi4Test;

// 000: a roundabout with spokes N, SE and SW. 101: an empty custom hyperlane (portal interchange). 102: a straight
// N-S hyperlane. 103: a system.
class HyperlanePortalsTest extends BaseTi4Test {

    private static final String ROUNDABOUT = "1,0,1,0,1,0;0,0,0,0,0,0;1,0,1,0,1,0;0,0,0,0,0,0;1,0,1,0,1,0;0,0,0,0,0,0";
    private static final String STRAIGHT = "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0";

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        hyperlane("000", ROUNDABOUT);
        hyperlane("101", null);
        hyperlane("102", STRAIGHT);
        game.setTile(new Tile("19", "103"));
    }

    @Test
    void parsesTheModalFormat() {
        HyperlanePortals.Typed typed = HyperlanePortals.parseTyped("5=c<, 0=A ; 3=B>");
        assertThat(typed.problem()).isEmpty();
        assertThat(typed.slots())
                .containsExactly(
                        new PortalSlot(0, 'A', Flow.BOTH),
                        new PortalSlot(3, 'B', Flow.ENTRY_ONLY),
                        new PortalSlot(5, 'C', Flow.EXIT_ONLY));
        assertThat(HyperlanePortals.describe(typed.slots())).isEqualTo("0=A, 3=B>, 5=C<");
    }

    // The first portal always sits in the middle; letters without a slot fill the free ones in order.
    @Test
    void theFirstPortalSitsInTheMiddle() {
        assertThat(HyperlanePortals.describe(
                        HyperlanePortals.parseTyped("B, C>, D<").slots()))
                .isEqualTo("0=B, 1=C>, 2=D<");
        assertThat(HyperlanePortals.describe(
                        HyperlanePortals.parseTyped("3=B, 5=C").slots()))
                .isEqualTo("0=B, 5=C");
        assertThat(HyperlanePortals.parseTyped("A, B, C, D, E, F, G, H").problem())
                .isPresent();
    }

    @Test
    void refusesBadSlotsAndTiles() {
        assertThat(HyperlanePortals.parseTyped("7=A").problem()).isPresent();
        assertThat(HyperlanePortals.parseTyped("1=A, 1=B").problem()).isPresent();
        assertThat(HyperlanePortals.parseTyped("1=A, 2=a").problem()).isPresent();
        assertThat(HyperlanePortals.problemWith(game, "102", "0=A")).isPresent();
        assertThat(HyperlanePortals.problemWith(game, "103", "0=A")).isPresent();
        assertThat(HyperlanePortals.problemWith(game, "000", "0=A")).isEmpty();
        assertThat(HyperlanePortals.problemWith(game, "101", "2=A, 5=K")).isEmpty();
    }

    @Test
    void storesAndFindsLetters() {
        HyperlanePortals.set(
                game, "000", HyperlanePortals.parseTyped("0=A, 3=B>").slots());
        HyperlanePortals.set(game, "101", HyperlanePortals.parseTyped("2=A").slots());
        assertThat(HyperlanePortals.positionsWithLetter(game, 'A')).containsExactly("000", "101");
        assertThat(HyperlanePortals.slotsOf(game, "000")).hasSize(2);

        HyperlanePortals.set(game, "101", List.of());
        assertThat(HyperlanePortals.positionsWithLetter(game, 'A')).containsExactly("000");
    }

    // Slots 1-6 turn with the hyperlane (60 degrees per step, clockwise); the centre slot stays.
    @Test
    void rotationTurnsTheSlotsBetweenTheSpokes() {
        HyperlanePortals.set(
                game, "000", HyperlanePortals.parseTyped("0=A, 1=B, 6=C").slots());
        HyperlanePortals.rotate(game, "000", 1);
        assertThat(HyperlanePortals.describe(HyperlanePortals.slotsOf(game, "000")))
                .isEqualTo("0=A, 1=C, 2=B");
    }

    // Portals travel with their hyperlane on moves and swaps, and the GM's tile commands clear them.
    @Test
    void portalsTravelWithTheirTileAndGmCommandsClearThem() {
        HyperlanePortals.set(game, "000", HyperlanePortals.parseTyped("0=A").slots());
        hyperlane("000", null);
        hyperlane("104", ROUNDABOUT);
        CustomHyperlaneService.moveCustomHyperlaneData("000", "104", game, true);
        assertThat(HyperlanePortals.slotsOf(game, "104")).hasSize(1);
        assertThat(HyperlanePortals.slotsOf(game, "000")).isEmpty();

        TileBoundData.moveAll(game, Map.of("104", "105"));
        hyperlane("105", ROUNDABOUT);
        assertThat(HyperlanePortals.slotsOf(game, "105")).hasSize(1);

        TileBoundData.clear(game, "105");
        assertThat(HyperlanePortals.hasAny(game)).isFalse();
    }

    // The modal input, and the compact export format surviving a round trip through import.
    @Test
    void modalInputAndImportExport() {
        assertThat(CustomHyperlaneService.savePortalInput(game, "000", "0=A, 3=B>"))
                .contains("A has no other end yet");
        assertThat(CustomHyperlaneService.savePortalInput(game, "101", "2=A")).contains("A links to 000");
        assertThat(CustomHyperlaneService.savePortalInput(game, "102", "0=A")).contains("isn't a roundabout");

        String compact = HyperlanePortals.encodeCompact(HyperlanePortals.slotsOf(game, "000"));
        assertThat(compact).isEqualTo("0A3B>");
        HyperlanePortals.clear(game);
        assertThat(CustomHyperlaneService.importHyperlaneData(game, "portal:000," + compact))
                .contains("Portals: 1 hyperlanes imported");
        assertThat(HyperlanePortals.describe(HyperlanePortals.slotsOf(game, "000")))
                .isEqualTo("0=A, 3=B>");
        assertThat(game.getCustomHyperlaneData()).containsKey("102");

        assertThat(CustomHyperlaneService.savePortalInput(game, "000", "")).isEqualTo("Portals removed from 000.");
        assertThat(CustomHyperlaneService.savePortalInput(game, "000", "3=A, 5=B"))
                .contains("A moved to the middle");
    }

    // Placing anything that isn't a custom hyperlane drops the position's portals, so they can't come back later.
    @Test
    void placingASystemDropsThePortals() {
        HyperlanePortals.set(game, "000", HyperlanePortals.parseTyped("A").slots());
        AddTileService.addTile(game, new Tile("19", "000"));
        assertThat(HyperlanePortals.hasAny(game)).isFalse();
    }

    private void hyperlane(String position, String matrix) {
        game.setTile(new Tile("hl", position));
        if (matrix == null) {
            game.getCustomHyperlaneData().remove(position);
        } else {
            game.getCustomHyperlaneData().put(position, matrix);
        }
    }
}
