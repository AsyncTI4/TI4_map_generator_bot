package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

// T'ro An places 2 infantry on a planet the player controls in the active system, not on any planet they control.
class SardakkAgentPlacementButtonsTest extends BaseTi4Test {

    private Game game;
    private Player player;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("tro-placement-test");
        player = game.addPlayer("troUser", "Tro Player");
        player.setFaction(game, "sol");
        player.setColor("blue");
        game.setTile(new Tile("26", "201"));
        game.setTile(new Tile("25", "202"));
        player.addPlanet("lodor");
        player.addPlanet("quann");
    }

    private static List<String> planetsOf(List<Button> buttons) {
        return buttons.stream()
                .map(button ->
                        button.getCustomId().substring(button.getCustomId().lastIndexOf('_') + 1))
                .toList();
    }

    @Test
    void onlyPlanetsInTheActiveSystemAreOffered() {
        game.setActiveSystem("201");

        assertThat(planetsOf(ButtonHelperAgents.sardakkAgentPlacementButtons(game, player)))
                .containsExactly("lodor");
    }

    @Test
    void theOffersPlaceTwoInfantry() {
        game.setActiveSystem("201");

        assertThat(ButtonHelperAgents.sardakkAgentPlacementButtons(game, player))
                .allSatisfy(button -> assertThat(button.getCustomId()).contains("placeOneNDone_skipbuild_2gf_"));
    }

    @Test
    void everyControlledPlanetIsOfferedWhenTheActiveSystemHoldsNone() {
        game.setActiveSystem("201");
        player.removePlanet("lodor");

        assertThat(planetsOf(ButtonHelperAgents.sardakkAgentPlacementButtons(game, player)))
                .containsExactly("quann");
    }

    @Test
    void everyControlledPlanetIsOfferedOutsideATacticalAction() {
        game.setActiveSystem("");

        assertThat(planetsOf(ButtonHelperAgents.sardakkAgentPlacementButtons(game, player)))
                .containsExactlyInAnyOrder("lodor", "quann");
    }
}
