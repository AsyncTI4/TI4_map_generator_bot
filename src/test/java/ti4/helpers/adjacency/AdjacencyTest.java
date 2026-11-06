package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.TestGameHarness;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.testUtils.BaseTi4Test;

// Board: game-with-border-anomalies with a gravity wave on one edge of 101, so the link runs 101 -> neighbour.
class AdjacencyTest extends BaseTi4Test {

    private static final String HOLDER = "101";

    private TestGameHarness harness;
    private Game game;
    private String neighbour;

    @BeforeEach
    void load() {
        harness = TestGameHarness.fromSourceGame("game-with-border-anomalies");
        game = harness.load();
        game.setFowMode(false);
        neighbour = gravityWaveNeighbour();
    }

    @AfterEach
    void close() {
        harness.close();
    }

    // One-way adjacency on, and fog with the toggle off, follow the direction for every purpose.
    @Test
    void onAndFogFollowTheDirection() {
        game.setOneWayAdjacency(true);
        Adjacency adjacency = Adjacency.of(game);
        assertThat(adjacency.from(HOLDER)).contains(neighbour);
        assertThat(adjacency.from(neighbour)).doesNotContain(HOLDER);
        assertThat(adjacency.into(neighbour)).contains(HOLDER);
        assertThat(adjacency.into(HOLDER)).doesNotContain(neighbour);
        assertThat(adjacency.either(neighbour)).contains(HOLDER);
        assertThat(adjacency.either(HOLDER)).contains(neighbour);

        game.setOneWayAdjacency(false);
        game.setFowMode(true);
        assertThat(Adjacency.of(game).forEffects().from(neighbour)).doesNotContain(HOLDER);
        assertThat(Adjacency.of(game).forVision().from(neighbour)).doesNotContain(HOLDER);
    }

    // Off (the default outside fog): a one-way link only restricts movement.
    @Test
    void offModeKeepsMovementOneWayAndEverythingElseBothWays() {
        assertThat(Adjacency.of(game).forMovement().from(neighbour)).doesNotContain(HOLDER);
        assertThat(Adjacency.of(game).forMovement().into(HOLDER)).doesNotContain(neighbour);
        for (Adjacency adjacency :
                List.of(Adjacency.of(game).forEffects(), Adjacency.of(game).forVision())) {
            assertThat(adjacency.from(neighbour)).contains(HOLDER);
            assertThat(adjacency.into(HOLDER)).contains(neighbour);
            assertThat(adjacency.isAdjacent(neighbour, HOLDER)).isTrue();
        }
    }

    // A border that blocks both ways stays blocked in off mode: the union of two missing links is still missing.
    @Test
    void offModeKeepsTwoWayBlocks() {
        game.setBorderAnomalies(new ArrayList<>());
        game.addBorderAnomaly(
                HOLDER, MapPositionMapper.getAdjacentTilePositions(HOLDER).indexOf(neighbour), "spatial_tear");

        assertThat(Adjacency.of(game).forEffects().from(HOLDER)).doesNotContain(neighbour);
        assertThat(Adjacency.of(game).forEffects().from(neighbour)).doesNotContain(HOLDER);
    }

    // A custom hyperlane at 000 with one lane entering at N (from 101) and leaving at S (to 104), typed as a 2.
    @Test
    void oneWayHyperlaneFollowsItsLane() {
        game.setBorderAnomalies(new ArrayList<>());
        game.setTile(new Tile("hl", "000"));
        CustomHyperlaneService.insertData(
                game, "000", "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        List<String> around = MapPositionMapper.getAdjacentTilePositions("000");
        String entry = around.get(0), exit = around.get(3);

        for (boolean oneWay : new boolean[] {false, true}) {
            game.setOneWayAdjacency(oneWay);
            Adjacency movement = Adjacency.of(game).forMovement();
            assertThat(movement.from(entry)).contains(exit);
            assertThat(movement.from(exit)).doesNotContain(entry);
            assertThat(movement.into(exit)).contains(entry);
            assertThat(movement.into(entry)).doesNotContain(exit);
            assertThat(Adjacency.of(game).forEffects().from(exit).contains(entry))
                    .as("effects against the lane, one-way adjacency " + oneWay)
                    .isEqualTo(!oneWay);
        }
        // the lanes a player's vision runs along, for "Hide unseen hyperlanes"
        assertThat(GridRule.hyperlaneLanesFrom(game, null, entry)).containsExactly("000:0:3");
        assertThat(GridRule.hyperlaneLanesFrom(game, null, exit)).isEmpty();
    }

    // FoW+ and Hide Unexplored Map gate entry into a hyperlane by the entered side's own lanes, so a one-way
    // lane is still usable in its direction (the gate used to look at the lanes leaving by that side).
    @Test
    void oneWayHyperlaneWorksWithHideUnexploredMap() {
        game.setBorderAnomalies(new ArrayList<>());
        game.setTile(new Tile("hl", "000"));
        CustomHyperlaneService.insertData(
                game, "000", "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        List<String> around = MapPositionMapper.getAdjacentTilePositions("000");
        game.setFowMode(true);
        game.setFowOption(FOWOption.HIDE_MAP, true);

        assertThat(Adjacency.of(game).forMovement().from(around.get(0))).contains(around.get(3));
        assertThat(Adjacency.of(game).forMovement().from(around.get(3))).doesNotContain(around.get(0));
    }

    // Off mode counts each one-way lane both ways for effects, also through a chain of hyperlanes whose lanes
    // point against each other (000 runs N -> S, the hyperlane south of it S -> N); movement still can't pass.
    @Test
    void offModeEffectsReadChainedOneWayHyperlanesBothWays() {
        game.setBorderAnomalies(new ArrayList<>());
        String south = MapPositionMapper.getAdjacentTilePositions("000").get(3);
        game.setTile(new Tile("hl", "000"));
        game.setTile(new Tile("hl", south));
        CustomHyperlaneService.insertData(
                game, "000", "0,0,0,2,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        CustomHyperlaneService.insertData(
                game, south, "0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;2,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");
        String north = MapPositionMapper.getAdjacentTilePositions("000").get(0);
        String farSouth = MapPositionMapper.getAdjacentTilePositions(south).get(3);

        assertThat(Adjacency.of(game).forEffects().from(north)).contains(farSouth);
        assertThat(Adjacency.of(game).forEffects().into(farSouth)).contains(north);
        assertThat(Adjacency.of(game).forMovement().from(north)).doesNotContain(farSouth);
        game.setOneWayAdjacency(true);
        assertThat(Adjacency.of(game).forEffects().from(north)).doesNotContain(farSouth);
    }

    // /map add_custom_adjacent_tiles primary:P adjacent:X links P -> X; movement follows it in every mode.
    @Test
    void customLinkRunsFromPrimaryToListed() {
        String far = AdjacencyScenarios.positions(game).stream()
                .filter(position -> !HOLDER.equals(position)
                        && !Adjacency.of(game).either(HOLDER).contains(position))
                .findFirst()
                .orElseThrow();
        game.addCustomAdjacentTiles(HOLDER, List.of(far));

        for (boolean oneWay : new boolean[] {false, true}) {
            game.setOneWayAdjacency(oneWay);
            Adjacency movement = Adjacency.of(game).forMovement();
            assertThat(movement.from(HOLDER)).contains(far);
            assertThat(movement.into(far)).contains(HOLDER);
            assertThat(movement.from(far)).doesNotContain(HOLDER);
        }
        for (Adjacency adjacency :
                List.of(Adjacency.of(game).forEffects(), Adjacency.of(game).forVision())) {
            assertThat(adjacency.from(HOLDER)).contains(far);
            assertThat(adjacency.from(far)).doesNotContain(HOLDER);
        }
    }

    // Deep flagship: "During movement, you may treat this unit's system as adjacent to your home system."
    @Test
    void deepFlagshipLinksItsSystemToHomeForMovementOnly() {
        Player deep = game.getRealPlayers().getFirst();
        deep.addOwnedUnitByID("deep_flagship");
        String home = deep.getHomeSystemTile().getPosition();
        String far = AdjacencyScenarios.positions(game).stream()
                .filter(position -> !home.equals(position)
                        && !Adjacency.of(game).either(home).contains(position))
                .findFirst()
                .orElseThrow();
        game.getTileByPosition(far).addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, deep.getColorID()), 1);

        Adjacency movement = Adjacency.of(game, deep).forMovement();
        assertThat(movement.from(far)).contains(home);
        assertThat(movement.from(home)).contains(far);
        assertThat(movement.into(home)).contains(far);
        assertThat(Adjacency.of(game, deep).forEffects().from(far)).doesNotContain(home);
    }

    // The position itself only when asked; callers change the returned sets, so a query never hands out its cache.
    @Test
    void selfOnlyWhenAskedAndResultsAreCopies() {
        Adjacency adjacency = Adjacency.of(game);
        assertThat(adjacency.from(HOLDER)).doesNotContain(HOLDER);
        assertThat(adjacency.withSelf().from(HOLDER)).contains(HOLDER);
        assertThat(adjacency.withSelf().into(HOLDER)).contains(HOLDER);
        assertThat(adjacency.isAdjacent(HOLDER, HOLDER)).isTrue();

        int size = adjacency.from(HOLDER).size();
        adjacency.from(HOLDER).add("not-a-tile");
        adjacency.into(HOLDER).clear();
        assertThat(adjacency.from(HOLDER)).hasSize(size).doesNotContain("not-a-tile");
        assertThat(adjacency.into(HOLDER)).isNotEmpty();
    }

    // Hyperlane tiles are not systems: they stay out of reach in off mode, and from/into still mirror each other.
    @Test
    void offModeIsSymmetricBetweenSystemsAndLeavesHyperlaneTilesEmpty() {
        try (var hyperlanes = TestGameHarness.fromSourceGame("pbd15036")) {
            Game map = hyperlanes.load();
            map.setFowMode(false);
            Adjacency adjacency = Adjacency.of(map).forEffects();
            List<String> positions = AdjacencyScenarios.positions(map);
            List<String> problems = new ArrayList<>();
            for (String a : positions) {
                boolean aIsHyperlane = map.getTileByPosition(a).getTileModel().isHyperlane();
                if (aIsHyperlane && !adjacency.from(a).isEmpty()) problems.add(a + " is a hyperlane tile with links");
                for (String b : positions) {
                    boolean bIsHyperlane =
                            map.getTileByPosition(b).getTileModel().isHyperlane();
                    if (adjacency.from(a).contains(b) != adjacency.into(b).contains(a)) {
                        problems.add(a + "->" + b + " from/into disagree");
                    }
                    if (!aIsHyperlane
                            && !bIsHyperlane
                            && adjacency.from(a).contains(b)
                                    != adjacency.from(b).contains(a)) {
                        problems.add(a + "<->" + b + " not symmetric");
                    }
                }
            }
            assertThat(problems).isEmpty();
        }
    }

    private String gravityWaveNeighbour() {
        List<String> neighbours = MapPositionMapper.getAdjacentTilePositions(HOLDER);
        for (int direction = 0; direction < neighbours.size(); direction++) {
            String candidate = neighbours.get(direction);
            if (game.getTileByPosition(candidate) != null && game.getAdjacentTileOverride(HOLDER, direction) == null) {
                game.setBorderAnomalies(new ArrayList<>());
                game.addBorderAnomaly(HOLDER, direction, "gravity_wave");
                return candidate;
            }
        }
        throw new AssertionError("Test map has no plain neighbour for " + HOLDER);
    }
}
