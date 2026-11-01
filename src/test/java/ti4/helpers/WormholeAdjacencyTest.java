package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.game.persistence.TestGameHarness;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.model.Mapper;
import ti4.model.WormholeModel.Wormhole;
import ti4.testUtils.BaseTi4Test;

class WormholeAdjacencyTest extends BaseTi4Test {

    // Tokens go on the space area of tile A and on the space area (or first planet) of tile B, two tiles with no
    // printed wormholes. Lambda/palace was connected by file-name parsing before; WormholeDataTest covers the rest
    // of those false matches at the data level.
    @ParameterizedTest(name = "{0} -> {1} ({2}) adjacent={3}")
    @CsvSource({
        "token_sigma_lambda.png, token_sigma_lambda.png, space, true",
        "token_sigma_lambda.png, token_sigma_palace_emperors.png, space, false",
        "token_wheta.png, token_custom_eronous_wheta.png, space, true",
        "token_whalpha.png, attachment_sigma_weirdway.png, planet, true",
        // Rat race wormholes only link to the same rat race wormhole.
        "token_whratalpha.png, token_whratalpha.png, space, true",
        "token_whratalpha.png, token_whalpha.png, space, false",
        "token_whalpha.png, token_whratalpha.png, space, false",
        "token_whratalpha.png, token_whratbeta.png, space, false",
    })
    void tokensConnectOnlyThroughDeclaredWormholes(String onA, String onB, String holderB, boolean adjacent) {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            List<Tile> tiles = wormholeFreeTilesWithPlanets(game);
            Tile a = tiles.get(0);
            Tile b = tiles.get(1);
            a.getSpaceUnitHolder().addToken(onA);
            UnitHolder target =
                    "planet".equals(holderB) ? b.getPlanetUnitHolders().getFirst() : b.getSpaceUnitHolder();
            target.addToken(onB);

            assertThat(FoWHelper.getWormholeAdjacencies(game, a.getPosition(), null))
                    .as(onA + " -> " + onB)
                    .matches(positions -> positions.contains(b.getPosition()) == adjacent);
        }
    }

    // Same type on both sides: a link needs a departure (plain or entry-only) on A and an arrival
    // (plain or exit-only) on B.
    @ParameterizedTest(name = "{0} -> {1} reachable={2}")
    @CsvSource({
        "whalpha, whalpha, true",
        "whalpha, whalpha_in, false",
        "whalpha, whalpha_out, true",
        "whalpha_in, whalpha, true",
        "whalpha_in, whalpha_in, false",
        "whalpha_in, whalpha_out, true",
        "whalpha_out, whalpha, false",
        "whalpha_out, whalpha_in, false",
        "whalpha_out, whalpha_out, false",
        "whbeta_in, whalpha, false",
        "whdelta_in, whdelta_out, true",
    })
    void oneWayWormholesLinkOnlyFromDepartureToArrival(String onA, String onB, boolean reachable) {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            List<Tile> tiles = farApartTiles(game, 2);
            String a = tiles.get(0).getPosition();
            String b = tiles.get(1).getPosition();
            tiles.get(0).getSpaceUnitHolder().addToken(Mapper.getTokenID(onA));
            tiles.get(1).getSpaceUnitHolder().addToken(Mapper.getTokenID(onB));

            assertThat(FoWHelper.getWormholeAdjacencies(game, a, null).contains(b))
                    .isEqualTo(reachable);
        }
    }

    @Test
    void oneWayWormholesDirectMovementAndVision() {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            Player player = game.getRealPlayers().getFirst();
            List<Tile> tiles = farApartTiles(game, 3);
            String entry = tiles.get(0).getPosition();
            String exit = tiles.get(1).getPosition();
            String otherEntry = tiles.get(2).getPosition();
            tiles.get(0).getSpaceUnitHolder().addToken(Mapper.getTokenID("whalpha_in"));
            tiles.get(1).getSpaceUnitHolder().addToken(Mapper.getTokenID("whalpha_out"));
            tiles.get(2).getSpaceUnitHolder().addToken(Mapper.getTokenID("whalpha_in"));

            assertThat(CheckDistanceHelper.getDistanceBetweenTwoTiles(game, player, entry, exit, true))
                    .isEqualTo(1);
            assertThat(CheckDistanceHelper.getDistanceBetweenTwoTiles(game, player, exit, entry, true))
                    .isGreaterThan(1);
            // One-way adjacency off: only movement follows the direction, vision counts the link both ways.
            assertThat(Adjacency.of(game, player).forVision().from(exit)).contains(entry);
            // On: vision follows movement, an entry-only system sees the exit, never another entry.
            game.setOneWayAdjacency(true);
            assertThat(Adjacency.of(game, player).forVision().from(entry))
                    .contains(exit)
                    .doesNotContain(otherEntry);
            assertThat(Adjacency.of(game, player).forVision().from(exit)).doesNotContain(entry, otherEntry);
        }
    }

    @Test
    void oneWayTokensAreNeverATypesDisplayImage() {
        // Lambda has no token_whlambda.png, so the wormhole view relies on this lookup for its icon.
        assertThat(Mapper.getWormholeTokenImagePath(Wormhole.LAMBDA)).isEqualTo("token_sigma_lambda.png");
        for (Wormhole wormhole : Wormhole.values()) {
            assertThat(Mapper.getOneWayWormholeToken(Mapper.getWormholeTokenImagePath(wormhole)))
                    .as(wormhole.name())
                    .isNull();
        }
    }

    @Test
    void reconstructionKeepsTheDirectionOfAOneWayAlpha() {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            game.addLaw("wormhole_recon", null);
            List<Tile> tiles = farApartTiles(game, 2);
            String entry = tiles.get(0).getPosition();
            String beta = tiles.get(1).getPosition();
            tiles.get(0).getSpaceUnitHolder().addToken(Mapper.getTokenID("whalpha_in"));
            tiles.get(1).getSpaceUnitHolder().addToken("token_whbeta.png");

            assertThat(FoWHelper.getWormholeAdjacencies(game, entry, null)).contains(beta);
            assertThat(FoWHelper.getWormholeAdjacencies(game, beta, null)).doesNotContain(entry);
        }
    }

    @Test
    void tileCountsEveryDeclaredWormholeOnAnyHolder() {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            Tile tile = wormholeFreeTilesWithPlanets(game).getFirst();
            tile.getSpaceUnitHolder().addToken("token_whdelta.png");
            tile.getSpaceUnitHolder().addToken("token_creussepsilon.png");
            tile.getPlanetUnitHolders().getFirst().addToken("attachment_sigma_weirdway.png");
            // The old FoWHelper check found the Creuss flagship on any holder, so a planet counts too.
            Player creuss = game.addPlayer("creuss-test", "creuss-test");
            creuss.setFaction("ghost");
            creuss.setColor("lightgray");
            creuss.addOwnedUnitByID("ghost_flagship");
            tile.addUnit(
                    tile.getPlanetUnitHolders().getFirst().getName(),
                    Units.getUnitKey(UnitType.Flagship, creuss.getColorID()),
                    1);

            assertThat(tile.getWormholes(game))
                    .containsExactlyInAnyOrder(
                            Wormhole.DELTA, Wormhole.EPSILON, Wormhole.ALPHA, Wormhole.BETA, Wormhole.DELTA);
        }
    }

    private static List<Tile> farApartTiles(Game game, int count) {
        List<Tile> picked = new ArrayList<>();
        for (Tile candidate : wormholeFreeTilesWithPlanets(game)) {
            Set<String> near = FoWHelper.getAdjacentTiles(game, candidate.getPosition(), null, false);
            if (picked.stream().noneMatch(tile -> near.contains(tile.getPosition()))) {
                picked.add(candidate);
            }
            if (picked.size() == count) return picked;
        }
        throw new IllegalStateException("map has fewer than " + count + " separated tiles");
    }

    private static List<Tile> wormholeFreeTilesWithPlanets(Game game) {
        game.getTileMap().values().forEach(t -> t.getUnitHolders().values().forEach(UnitHolder::removeAllTokens));
        return game.getTileMap().values().stream()
                .filter(t -> Mapper.getWormholes(t.getTileID()).isEmpty())
                .filter(t -> !t.getPlanetUnitHolders().isEmpty())
                .toList();
    }
}
