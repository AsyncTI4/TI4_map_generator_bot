package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.image.draw.RowWrap;
import ti4.image.playerarea.PlayerAreaColumns;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

class MegaGamePlayerAreasTest extends BaseTi4Test {

    private static final List<String> FACTIONS =
            List.of("sol", "hacan", "jolnar", "letnev", "xxcha", "mentak", "yin", "yssaril", "naalu", "l1z1x");
    private static final List<String> COLORS =
            List.of("blue", "yellow", "purple", "red", "green", "orange", "black", "pink", "gray", "lightgray");

    private static final int MIN_PLAYER_AREA_HEIGHT = 300;
    private static final int GENEROUS_TAIL = 80;

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("mega-game-player-areas-test");
        MapFrame.positionsWithin("000", 3).forEach(position -> game.setTile(new Tile("19", position)));
    }

    private static int lastNonBlackRow(BufferedImage image) {
        for (int y = image.getHeight() - 1; y >= 0; y--) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) {
                    return y;
                }
            }
        }
        return -1;
    }

    private void seatPlayers(int count) {
        for (int i = 0; i < count; i++) {
            String faction = FACTIONS.get(i);
            Player player = game.addPlayer("p" + i, faction);
            player.setFaction(game, faction);
            player.setColor(COLORS.get(i));
            player.setUnitsOwned(new HashSet<>(Mapper.getFaction(faction).getUnits()));
        }
    }

    @Test
    void wrappingFillsEachRowBeforeStartingTheNext() {
        List<List<Integer>> rows = RowWrap.wrapIntoRows(List.of(40, 40, 40, 40), w -> w, 10, 100, 100);

        assertEquals(List.of(List.of(40, 40), List.of(40, 40)), rows);
    }

    // The last header row shares its height with the leaders, so only the tail that fits beside them stays on it.
    @Test
    void wrappingMovesOnlyTheTailThatFitsTheNarrowerLastRow() {
        List<List<Integer>> rows = RowWrap.wrapIntoRows(List.of(40, 40, 40, 40, 40), w -> w, 10, 300, 100);

        assertEquals(List.of(List.of(40, 40, 40), List.of(40, 40)), rows);
    }

    @Test
    void aLastRowThatAlreadyFitsIsLeftAlone() {
        List<List<Integer>> rows = RowWrap.wrapIntoRows(List.of(40, 40, 40, 40), w -> w, 10, 140, 50);

        assertEquals(List.of(List.of(40, 40, 40), List.of(40)), rows);
    }

    // 7 labels that fill 5 per row would leave a stub of 2; balancing gives rows of 4 and 3 instead.
    @Test
    void balancedRowsSpreadItemsEvenlyAcrossTheRowsNeeded() {
        List<List<Integer>> rows = RowWrap.balancedRows(List.of(20, 20, 20, 20, 20, 20, 20), w -> w, 0, 100);

        assertEquals(List.of(List.of(20, 20, 20, 20), List.of(20, 20, 20)), rows);
    }

    @Test
    void anItemWiderThanTheRowStillGetsARowOfItsOwn() {
        List<List<Integer>> rows = RowWrap.wrapIntoRows(List.of(300, 20), w -> w, 10, 100, 100);

        assertEquals(List.of(List.of(300), List.of(20)), rows);
    }

    @Test
    void eightPlayersKeepOneColumn() {
        seatPlayers(8);

        assertEquals(1, PlayerAreaColumns.split(game, game.getRealPlayers()).size());
    }

    @Test
    void moreThanEightPlayersSplitIntoTwoColumnsWithTheExtraAreaOnTheLeft() {
        seatPlayers(9);

        List<List<Player>> columns = PlayerAreaColumns.split(game, game.getRealPlayers());

        assertEquals(2, columns.size());
        assertEquals(5, columns.get(0).size());
        assertEquals(4, columns.get(1).size());
    }

    // The extra map column only made room for map-side player areas, which mega games don't draw.
    @Test
    void megaGameMapGetsNoExtraColumnForMapSidePlayerAreas() {
        seatPlayers(10);

        assertEquals(BoardLayout.smallestRegularPlayerAreaWidth() + 10, BoardLayout.getMapWidth(game, 3));
    }

    @Test
    void megaGameStatsAreLaidOutInTwoColumns() {
        seatPlayers(10);

        RenderContext context = MapRenderer.resolve(new MapRenderRequest(game, DisplayType.stats, null, null));
        int twoColumns = 10 + 2 * BoardLayout.smallestRegularPlayerAreaWidth() + PlayerAreaColumns.COLUMN_GAP;
        assertTrue(context.canvas().width() >= twoColumns, "both columns fit side by side");
        BufferedImage image = MapRenderer.paint(context, RenderTimings.disabled());
        assertTrue(image.getHeight() < 10 * MIN_PLAYER_AREA_HEIGHT, "five areas per column, not ten stacked");
        int tail = image.getHeight() - 1 - lastNonBlackRow(image);
        assertTrue(tail >= 0 && tail < GENEROUS_TAIL, "tail below the last drawn pixel was " + tail);
    }
}
