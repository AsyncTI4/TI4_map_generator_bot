package ti4.ai.naalu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Iconoclast (Thunder's Edge): when another player gains a relic, the bot offers its owner a free mech on a planet it
// controls. A free mech is always worth taking, so the seat (the test game's "nekro" seat, given the mech) deploys it.
class IconoclastRulesTest extends BaseTi4Test {

    private static final String TEXT =
            "you have Iconoclast, and somebody just drew a relic. You may use the buttons to DEPLOY 1 Iconoclast onto"
                    + " a planet you control:";
    private static final String HOME_MECH = "FFCC_nekro_" + IconoclastRules.PLACE_PREFIX + "mordaiii";
    private static final String LODOR_MECH = "FFCC_nekro_" + IconoclastRules.PLACE_PREFIX + "lodor";

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        home = test.nekroHome();
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("lodor");
        Set<String> units = new HashSet<>(test.nekro.getUnitsOwned());
        units.add(IconoclastRules.MECH);
        test.nekro.setUnitsOwned(units);
    }

    private static AiPrompt offer(String content) {
        return AiTestGame.withContent(
                prompt("icono", PromptSource.PUBLIC, NOW, HOME_MECH, LODOR_MECH, "deleteButtons"), content);
    }

    private String deploy(AiPrompt prompt) {
        return IconoclastRules.deploy(test.context(prompt))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    @Test
    void deploysTheFreeMech() {
        assertThat(deploy(offer(TEXT))).isIn(HOME_MECH, LODOR_MECH);
    }

    // Other flows (Hope's End, ...) use the same placement buttons; only the Iconoclast offer is answered here.
    @Test
    void leavesOtherMechPlacementsAlone() {
        assertThat(deploy(offer("Please choose a planet to drop a mech on."))).isEmpty();
    }

    @Test
    void needsTheIconoclast() {
        test.nekro.setUnitsOwned(new HashSet<>(Set.of("nekro_mech")));

        assertThat(deploy(offer(TEXT))).isEmpty();
    }

    @Test
    void declinesWhenAllMechsAreOnTheBoard() {
        test.units(home, "mordaiii", test.nekro, UnitType.Mech, 4);

        assertThat(deploy(offer(TEXT))).isEqualTo("deleteButtons");
    }
}
