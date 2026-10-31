package ti4.helpers.adjacency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.TestGameHarness;
import ti4.helpers.Constants;
import ti4.testUtils.BaseTi4Test;

// PR-R: card texts that the old code applied one way, or to the wrong player. Each test cites its source.
class AdjacencyRulingsTest extends BaseTi4Test {

    private static final String ALPHA = "token_whalpha.png";
    private static final String BETA = "token_whbeta.png";

    private TestGameHarness harness;
    private Game game;
    private Player first;
    private Player second;

    @BeforeEach
    void load() {
        harness = TestGameHarness.fromSourceGame("game-with-border-anomalies");
        game = harness.load();
        game.setFowMode(false);
        game.setOneWayAdjacency(true);
        first = game.getRealPlayers().getFirst();
        second = game.getRealPlayers().getLast();
    }

    @AfterEach
    void close() {
        harness.close();
    }

    // FSD: "You may treat wormholes as ingress tokens"; fracture.md V: "Each ingress is adjacent to each egress, and
    // vice versa". Only the FSD owner gets it, whoever is active.
    @Test
    void wormholesTreatedAsIngressWorkBothWaysForTheirOwnerOnly() {
        List<Tile> tiles = apartTiles(2);
        Tile wormhole = tiles.get(0);
        Tile egress = tiles.get(1);
        wormhole.getSpaceUnitHolder().addToken(ALPHA);
        egress.getSpaceUnitHolder().addToken(Constants.TOKEN_EGRESS);
        first.addTech("fsd");
        game.setActivePlayerID(second.getUserID());

        Adjacency owner = Adjacency.of(game, first).forMovement();
        assertThat(owner.from(wormhole.getPosition())).contains(egress.getPosition());
        assertThat(owner.from(egress.getPosition())).contains(wormhole.getPosition());
        Adjacency other = Adjacency.of(game, second).forMovement();
        assertThat(other.from(wormhole.getPosition())).doesNotContain(egress.getPosition());
        assertThat(other.from(egress.getPosition())).doesNotContain(wormhole.getPosition());
    }

    // Sever: "wormholes in that system have no effect during movement". Both ends, and only movement.
    @Test
    void severBlocksMovementBothWaysAndNothingElse() {
        List<Tile> tiles = apartTiles(2);
        Tile severed = tiles.get(0);
        Tile partner = tiles.get(1);
        severed.getSpaceUnitHolder().addToken(ALPHA);
        severed.getSpaceUnitHolder().addToken(Constants.TOKEN_SEVERED);
        partner.getSpaceUnitHolder().addToken(ALPHA);

        Adjacency movement = Adjacency.of(game).forMovement();
        assertThat(movement.from(severed.getPosition())).doesNotContain(partner.getPosition());
        assertThat(movement.from(partner.getPosition())).doesNotContain(severed.getPosition());
        Adjacency effects = Adjacency.of(game).forEffects();
        assertThat(effects.from(severed.getPosition())).contains(partner.getPosition());
        assertThat(effects.from(partner.getPosition())).contains(severed.getPosition());
    }

    // Lazax Gate Folding: "During your tactical actions, if you do not control Mecatol Rex, treat its system as if
    // it contains both an alpha and beta wormhole." The system has them, so every player sees both ends.
    @Test
    void gateFoldingLinksMecatolBothWaysForEveryPlayerDuringTheTacticalAction() {
        Tile mecatol = game.getTileMap().values().stream()
                .filter(tile -> tile.getUnitHolders().containsKey("mr")
                        || tile.getUnitHolders().containsKey("mrte"))
                .findFirst()
                .orElseThrow();
        Tile alpha = apartFrom(mecatol);
        alpha.getSpaceUnitHolder().addToken(ALPHA);
        Player winnu = game.getRealPlayers().stream()
                .filter(player -> !player.getPlanets().contains("mr")
                        && !player.getPlanets().contains("mrte"))
                .findFirst()
                .orElseThrow();
        Player bystander = winnu == first ? second : first;
        winnu.addTech("lgf");
        game.setActivePlayerID(winnu.getUserID());
        game.setActiveSystem(alpha.getPosition());

        Adjacency adjacency = Adjacency.of(game, bystander).forMovement();
        assertThat(adjacency.from(alpha.getPosition())).contains(mecatol.getPosition());
        assertThat(adjacency.from(mecatol.getPosition())).contains(alpha.getPosition());
    }

    // Ghost agent: "that system is adjacent to all other systems that contain a wormhole during this tactical
    // action". Only the activated system gains links; two other systems stay apart.
    @Test
    void ghostAgentLinksOnlyTheActivatedSystem() {
        List<Tile> tiles = apartTiles(3);
        Tile activated = tiles.get(0);
        Tile beta = tiles.get(1);
        Tile alpha = tiles.get(2);
        activated.getSpaceUnitHolder().addToken(ALPHA);
        beta.getSpaceUnitHolder().addToken(BETA);
        alpha.getSpaceUnitHolder().addToken(ALPHA);
        game.setActivePlayerID(second.getUserID());
        game.setActiveSystem(activated.getPosition());
        game.setStoredValue("ghostagent_active", activated.getPosition());

        Adjacency adjacency = Adjacency.of(game, first).forMovement();
        assertThat(adjacency.from(activated.getPosition())).contains(beta.getPosition());
        assertThat(adjacency.from(beta.getPosition())).contains(activated.getPosition());
        assertThat(adjacency.from(beta.getPosition())).doesNotContain(alpha.getPosition());
    }

    // Systems with planets and no wormholes, none adjacent to another, so only the tokens the test adds link them.
    private List<Tile> apartTiles(int count) {
        List<Tile> picked = new ArrayList<>();
        for (Tile tile : candidates()) {
            if (picked.stream().allMatch(other -> isApart(tile, other))) picked.add(tile);
            if (picked.size() == count) return picked;
        }
        throw new AssertionError("Test map has no " + count + " separate systems");
    }

    private Tile apartFrom(Tile anchor) {
        return candidates().stream()
                .filter(tile -> isApart(tile, anchor))
                .findFirst()
                .orElseThrow();
    }

    private List<Tile> candidates() {
        return game.getTileMap().values().stream()
                .filter(tile -> !tile.getPlanetUnitHolders().isEmpty())
                .filter(tile -> tile.getWormholes(game).isEmpty())
                .filter(tile -> tile.getUnitHolders().keySet().stream().noneMatch(name -> name.startsWith("mr")))
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList();
    }

    private boolean isApart(Tile tile, Tile other) {
        return tile != other && !Adjacency.of(game).either(other.getPosition()).contains(tile.getPosition());
    }
}
