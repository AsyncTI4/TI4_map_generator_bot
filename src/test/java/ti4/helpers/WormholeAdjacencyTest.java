package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.game.persistence.TestGameHarness;
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

            assertThat(FoWHelper.getWormholeAdjacencies(game, a.getPosition(), null, false))
                    .as(onA + " -> " + onB)
                    .matches(positions -> positions.contains(b.getPosition()) == adjacent);
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

            assertThat(tile.getWormholes(game))
                    .containsExactlyInAnyOrder(Wormhole.DELTA, Wormhole.EPSILON, Wormhole.ALPHA, Wormhole.BETA);
        }
    }

    private static List<Tile> wormholeFreeTilesWithPlanets(Game game) {
        game.getTileMap().values().forEach(t -> t.getUnitHolders().values().forEach(UnitHolder::removeAllTokens));
        return game.getTileMap().values().stream()
                .filter(t -> Mapper.getWormholes(t.getTileID()).isEmpty())
                .filter(t -> !t.getPlanetUnitHolders().isEmpty())
                .toList();
    }
}
