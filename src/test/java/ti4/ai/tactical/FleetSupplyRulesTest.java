package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Building a carrier past the fleet limit leaves one ship too many at home. The bot then posts a "violating fleet pool
// limits" warning with a button that opens the remove-units buttons; the AI opens it, removes its cheapest ship and
// closes the buttons once the fleet fits again.
class FleetSupplyRulesTest extends BaseTi4Test {

    private static final String OPEN = "getDamageButtons_" + AiTestGame.HOME + "_remove";
    private static final String REMOVE = "FFCC_nekro_assignHits_" + AiTestGame.HOME + "_1_";

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
        test.nekro.setFleetCC(3);
        test.units(home, "space", test.nekro, UnitType.Dreadnought, 1);
        test.units(home, "space", test.nekro, UnitType.Carrier, 2);
        test.units(home, "space", test.nekro, UnitType.Cruiser, 1);
        test.units(home, "space", test.nekro, UnitType.Fighter, 2);
    }

    private AiPrompt warning() {
        return AiTestGame.withContent(
                prompt("warning", PromptSource.PUBLIC, NOW, OPEN, "deleteButtons"),
                test.nekro.getRepresentationUnfogged() + ", you are violating fleet pool limits in the system.");
    }

    private AiPrompt removals() {
        return prompt(
                "remove",
                PromptSource.PUBLIC,
                NOW + 1,
                List.of(
                        "FFCC_nekro_assignDamage_" + AiTestGame.HOME + "_1_dn_black",
                        REMOVE + "dn_black",
                        REMOVE + "cv_black",
                        "FFCC_nekro_assignHits_" + AiTestGame.HOME + "_2_cv_black",
                        REMOVE + "ca_black",
                        REMOVE + "ff_black",
                        "deleteButtons"),
                List.of());
    }

    private String decide(AiPrompt... prompts) {
        return FleetSupplyRules.removeExcessShips(test.context(prompts))
                .map(AiTestGame::pressedId)
                .orElse("nothing");
    }

    @Test
    void opensTheRemovalButtonsWhenOverTheFleetLimit() {
        assertThat(decide(warning())).isEqualTo(OPEN);
    }

    @Test
    void removesTheCheapestShipThenClosesTheButtons() {
        decide(warning());
        test.game.setStoredValue("nekrolatestAssignHits", "remove");

        assertThat(decide(warning(), removals())).isEqualTo(REMOVE + "ca_black");

        home.removeUnit("space", ti4.helpers.Units.getUnitKey(UnitType.Cruiser, "black"), 1);

        assertThat(decide(warning(), removals())).isEqualTo("deleteButtons");
    }

    // The limit is the bot's own: Armada raises it by two, so four ships under three fleet tokens are legal and a
    // stale warning gets no removal at all.
    @Test
    void removesNothingWhenAFleetRaisingAbilityMakesTheFleetLegal() {
        test.nekro.addAbility("armada");
        test.game.setStoredValue("nekrolatestAssignHits", "remove");

        assertThat(decide(warning())).isEqualTo("nothing");
        assertThat(decide(warning(), removals())).isEqualTo("nothing");
    }

    // A carrier is cheaper than the dreadnought, but its eight infantry fill the space area's capacity: removing a
    // carrier would break the bot's capacity check, so the dreadnought goes instead.
    @Test
    void keepsALoadedCarrierWhoseCargoWouldNoLongerFit() {
        test.nekro.setFleetCC(2);
        home.removeUnit("space", ti4.helpers.Units.getUnitKey(UnitType.Cruiser, "black"), 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 8);
        test.game.setStoredValue("nekrolatestAssignHits", "remove");

        AiPrompt removals = prompt(
                "remove",
                PromptSource.PUBLIC,
                NOW + 1,
                List.of(REMOVE + "dn_black", REMOVE + "cv_black", REMOVE + "ff_black", "deleteButtons"),
                List.of());

        assertThat(decide(warning(), removals)).isEqualTo(REMOVE + "dn_black");
    }

    // Within the fleet limit (another seat's warning, or one already resolved) the AI leaves its ships alone.
    @Test
    void leavesItsShipsAloneWithinTheFleetLimit() {
        test.nekro.setFleetCC(4);
        test.game.setStoredValue("nekrolatestAssignHits", "remove");

        assertThat(decide(warning(), removals())).isEqualTo("nothing");
    }
}
