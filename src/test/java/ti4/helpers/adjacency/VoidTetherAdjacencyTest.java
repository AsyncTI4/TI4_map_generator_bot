package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.json.JsonMapperManager;
import ti4.model.BorderAnomalyHolder;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Void Tether ruling (docs/rules/factions/empyrean.md, Void Tether I): the shared edge is blocked for other players
 * for all game effects, the Empyrean still crosses it. Only the edge: wormholes or custom links between the same two
 * systems still connect them. Enforced with one-way adjacency on (and in fog) only.
 * Board: Mecatol plus ring 1, tether between 000 and 101.
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
        game.setOneWayAdjacency(true);
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

    // Normal games (one-way adjacency off, no fog) draw the tether but do not enforce it.
    @Test
    void normalGamesDoNotEnforceTheTether() {
        game.setOneWayAdjacency(false);

        assertThat(Adjacency.of(game, hacan).forMovement().from("000")).contains("101");
        assertThat(Adjacency.of(game).from("101")).contains("000");
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

        assertThat(letnev.getNeighbouringPlayers(true)).contains(empyrean).doesNotContain(hacan);
        assertThat(empyrean.getNeighbouringPlayers(false)).contains(letnev);
        assertThat(hacan.getNeighbouringPlayers(true)).doesNotContain(letnev);
    }

    // Abilities use the acting player's one-way set: Letnev cannot reach across, so in fog it must not learn the
    // Empyrean sits behind the tether (Naalu commander, Reflect, neighbour counts).
    @Test
    void onlyTheExemptSideGetsTheOneWayNeighbour() {
        placeCruiser(empyrean, "000");
        placeCruiser(letnev, "101");

        assertThat(letnev.getAbilityNeighbours()).doesNotContain(empyrean);
        assertThat(empyrean.getAbilityNeighbours()).contains(letnev);

        game.getBorderAnomalies().getFirst().allow(letnev.getColor());
        assertThat(letnev.getAbilityNeighbours()).contains(empyrean);
    }

    // GM "who can see" asks each player's own rules, so the holder still shows up across its tether.
    @Test
    void gmViewCountsTheHolderAcrossItsTether() {
        placeCruiser(empyrean, "101");

        assertThat(FoWHelper.getAdjacentPlayers(game, "000", false, AdjacencyPurpose.VISION))
                .contains(empyrean);

        empyrean.removeBreakthrough("empyreanbt");
        assertThat(FoWHelper.getAdjacentPlayers(game, "000", false, AdjacencyPurpose.VISION))
                .doesNotContain(empyrean);
    }

    @Test
    void anotherBorderOnTheSameEdgeStillSeparatesThem() {
        placeCruiser(empyrean, "000");
        placeCruiser(letnev, "101");
        game.addBorderAnomaly("101", 3, "spatial_tear");

        assertThat(empyrean.getNeighbouringPlayers(true)).doesNotContain(letnev);
    }

    // "unless you allow it": the holder opens one token for chosen colours; moving or removing the token resets it.
    @Test
    void allowedColourIsExemptUntilTheTokenIsReplaced() {
        game.getBorderAnomalies().getFirst().allow(hacan.getColor());

        assertThat(Adjacency.of(game, hacan).forMovement().from("000")).contains("101");
        assertThat(Adjacency.of(game, letnev).forMovement().from("000")).doesNotContain("101");

        game.removeBorderAnomaly("000", 0);
        game.addBorderAnomaly("000", 0, "void_tether");
        assertThat(Adjacency.of(game, hacan).forMovement().from("000")).doesNotContain("101");
    }

    @Test
    void allowedColoursSurviveTheSaveFormat() throws Exception {
        BorderAnomalyHolder tether = game.getBorderAnomalies().getFirst();
        JsonMapper mapper = JsonMapperManager.basic();
        assertThat(mapper.writeValueAsString(tether)).doesNotContain("allowedColors");

        tether.allow(hacan.getColor());
        BorderAnomalyHolder loaded = mapper.readValue(mapper.writeValueAsString(tether), BorderAnomalyHolder.class);
        assertThat(loaded.isAllowed(hacan.getColor())).isTrue();
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
