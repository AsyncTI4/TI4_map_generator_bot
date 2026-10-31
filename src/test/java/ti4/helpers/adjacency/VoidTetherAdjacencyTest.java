package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * Void Tether ruling (docs/rules/factions/empyrean.md, Void Tether I): other players treat the two systems as not
 * adjacent for all game effects, the Empyrean still does. Board: Mecatol plus ring 1, tether between 000 and 101.
 */
class VoidTetherAdjacencyTest extends BaseTi4Test {

    private static final String PLAIN_SYSTEM = "19";

    private Game game;
    private Player empyrean;
    private Player hacan;
    private Player letnev;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("void-tether-test");
        game.setTile(new Tile(PLAIN_SYSTEM, "000"));
        for (int index = 1; index <= 6; index++) {
            game.setTile(new Tile(PLAIN_SYSTEM, "10" + index));
        }
        game.addBorderAnomaly("000", 0, "void_tether");
        empyrean = addPlayer("empyrean", "purple");
        hacan = addPlayer("hacan", "yellow");
        letnev = addPlayer("letnev", "red");
        if (!empyrean.hasBreakthrough("empyreanbt")) empyrean.addBreakthrough("empyreanbt");
    }

    @Test
    void otherPlayersAndNoPlayerAreBlockedBothWaysForEveryPurpose() {
        for (Adjacency adjacency : new Adjacency[] {
            Adjacency.of(game),
            Adjacency.of(game, hacan),
            Adjacency.of(game, hacan).forMovement()
        }) {
            assertThat(adjacency.from("000")).doesNotContain("101").contains("102");
            assertThat(adjacency.from("101")).doesNotContain("000");
            assertThat(adjacency.into("101")).doesNotContain("000");
        }
    }

    @Test
    void breakthroughHolderStillTreatsThemAsAdjacent() {
        for (Adjacency adjacency : new Adjacency[] {
            Adjacency.of(game, empyrean), Adjacency.of(game, empyrean).forMovement()
        }) {
            assertThat(adjacency.from("000")).contains("101");
            assertThat(adjacency.from("101")).contains("000");
            assertThat(adjacency.into("000")).contains("101");
        }
    }

    @Test
    void neighboursAcrossTheTetherOnlyWhenTheHolderIsOneOfThem() {
        // Void Tether I.γ: Hacan (000) and Letnev (101) are not neighbours; the Empyrean (000) is, both ways.
        placeCruiser(hacan, "000");
        placeCruiser(empyrean, "000");
        placeCruiser(letnev, "101");

        assertThat(letnev.getNeighbouringPlayers(false)).contains(empyrean).doesNotContain(hacan);
        assertThat(empyrean.getNeighbouringPlayers(false)).contains(letnev);
        assertThat(hacan.getNeighbouringPlayers(true)).doesNotContain(letnev);
    }

    private void placeCruiser(Player player, String position) {
        game.getTileByPosition(position).addUnit("space", Units.getUnitKey(UnitType.Cruiser, player.getColorID()), 1);
    }

    private Player addPlayer(String faction, String color) {
        Player player = game.addPlayer(faction + "-user", faction);
        player.setFaction(game, faction);
        player.setColor(color);
        return player;
    }
}
