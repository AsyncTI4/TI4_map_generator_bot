package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// G'hom Sek'kus: "commit up to 1 ground force from each planet" in and beside the active system. The buttons used to
// come back after every landing, so one planet could give a force again and again.
class SardakkCommanderButtonsTest extends BaseTi4Test {

    private static final String NEW_ALBION = "newalbion";
    private static final String STARPOINT = "starpoint";

    private AiTestGame test;
    private Tile source;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        String sourcePosition = AiTestGame.neighbourOf(AiTestGame.HOME);
        source = test.place("27", sourcePosition);
        String targetPosition = MapPositionMapper.getAdjacentTilePositions(sourcePosition).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .findFirst()
                .orElseThrow();
        test.place("26", targetPosition);
        test.nekro.addPlanet(NEW_ALBION);
        test.nekro.addPlanet(STARPOINT);
        test.units(source, NEW_ALBION, test.nekro, UnitType.Infantry, 2);
        test.units(source, NEW_ALBION, test.nekro, UnitType.Mech, 1);
        test.units(source, STARPOINT, test.nekro, UnitType.Infantry, 2);
        test.game.setActiveSystem(targetPosition);
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + targetPosition + ".");
    }

    private List<String> ids() {
        return ButtonHelperCommanders.getSardakkCommanderButtons(test.game, test.nekro, null).stream()
                .map(Button::getCustomId)
                .toList();
    }

    @Test
    void offersEachPlanetsInfantryAndMech() {
        assertThat(ids())
                .containsExactlyInAnyOrder(
                        "sardakkcommander_infantry_lodor_" + NEW_ALBION,
                        "sardakkcommander_mech_lodor_" + NEW_ALBION,
                        "sardakkcommander_infantry_lodor_" + STARPOINT);
    }

    @Test
    void stopsOfferingAPlanetOnceItHasGivenAForce() {
        ButtonHelperCommanders.rememberSardakkCommanderSource(test.game, test.nekro, NEW_ALBION);

        assertThat(ids()).containsExactly("sardakkcommander_infantry_lodor_" + STARPOINT);
    }

    @Test
    void offersTheForcesAgainOnTheNextActivation() {
        ButtonHelperCommanders.rememberSardakkCommanderSource(test.game, test.nekro, NEW_ALBION);
        String summary = test.game.getStoredValue("currentActionSummarynekro");
        test.game.setStoredValue("currentActionSummarynekro", summary + " Activated 000.");

        assertThat(ids()).hasSize(3);
    }
}
