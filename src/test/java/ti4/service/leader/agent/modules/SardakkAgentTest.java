package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.modules.SardakkAgent.Choice;
import ti4.service.leader.agent.modules.SardakkAgent.OfferPlanets;
import ti4.service.leader.agent.modules.SardakkAgent.PlaceOnPlanet;
import ti4.testUtils.BaseTi4Test;

class SardakkAgentTest extends BaseTi4Test {

    private final SardakkAgent module = new SardakkAgent();
    private Game game;
    private Player sardakk;
    private Player other;
    private Tile tile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("sardakk-agent-test");
        sardakk = game.addPlayer("sardakkUser", "Sardakk Player");
        sardakk.setFaction(game, "sardakk");
        sardakk.setColor("red");
        other = game.addPlayer("otherUser", "Other Player");
        other.setFaction(game, "pi_hacan");
        other.setColor("blue");
        tile = new Tile("26", "101");
        game.setTile(tile);
    }

    @Test
    void offersTheLegacyCombatButtonId() {
        assertThat(SardakkAgent.planetButtonId("101", "lodor")).isEqualTo("exhaustAgent_sardakkagent_101_lodor");
    }

    @Test
    void withoutAPayloadTheUserPicksAPlanet() {
        assertThat(module.decode(game, sardakk, "")).contains(new OfferPlanets(sardakk));
    }

    @Test
    void aTargetPayloadLetsThatPlayerPickAPlanet() {
        assertThat(module.decode(game, sardakk, "blue")).contains(new OfferPlanets(other));
    }

    @Test
    void aFactionContainingTheSeparatorIsATargetNotAPositionAndPlanet() {
        // The old split("_") parsing read "pi_hacan" as position "pi", planet "hacan" and crashed on the null tile.
        assertThat(module.decode(game, sardakk, "pi_hacan")).contains(new OfferPlanets(other));
    }

    @Test
    void aPositionAndPlanetPayloadPlacesDirectly() {
        assertThat(module.decode(game, sardakk, "101_lodor")).contains(new PlaceOnPlanet(tile, "lodor"));
    }

    @Test
    void anUnknownPositionCannotResolve() {
        assertThat(module.decode(game, sardakk, "999_lodor")).isEmpty();
    }

    @Test
    void placingPutsTwoInfantryOnThePlanet() {
        Choice choice = module.decode(game, sardakk, "101_lodor").orElseThrow();

        AgentOutcome outcome = module.resolve(AgentUse.of(
                module,
                game,
                sardakk,
                sardakk.getLeader(SardakkAgent.ID).orElseThrow(),
                SardakkAgent.ID,
                choice,
                null));

        assertThat(tile.getUnitHolders().get("lodor").getUnitCount(UnitType.Infantry, sardakk.getColor()))
                .isEqualTo(2);
        assertThat(outcome.messages())
                .singleElement()
                .satisfies(message -> assertThat(message.recipient()).isSameAs(sardakk));
    }
}
