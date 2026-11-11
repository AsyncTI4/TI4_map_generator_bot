package ti4.ai.naalu;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Naalu rules are wired into the brain for a real Naalu seat: its home system (Maaluuk and Druaa) sits at the test
// game's home position and Sol, the human, has the turn.
class NaaluBrainTest extends BaseTi4Test {

    private final NekroBrain brain = new NekroBrain();

    private AiTestGame test;
    private Player naalu;
    private Tile lodor;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(3);
        naalu = test.addSeat(AiTestGame.SECOND_AI_ID, "naalu", "green");
        Tile home = test.place("09", AiTestGame.HOME);
        naalu.addPlanet("maaluuk");
        naalu.addPlanet("druaa");
        test.units(home, "maaluuk", naalu, UnitType.Spacedock, 1);
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        lodor = test.place("26", position);
        naalu.setTacticalCC(3);
        naalu.setStrategicCC(3);
        naalu.setFleetCC(5);
        test.isActive(test.sol, "action");
        test.game.setActiveSystem(position);
    }

    private Optional<String> decide(AiPrompt... prompts) {
        AiTurnContext context = test.contextFor(naalu, Set.of(), NOW, prompts);
        AiDecision decision = brain.decide(context);
        return decision instanceof AiDecision.Press ? Optional.of(AiTestGame.pressedId(decision)) : Optional.empty();
    }

    @Test
    void escapesWithForesightBeforeTheFirstRoll() {
        test.units(lodor, "space", naalu, UnitType.Cruiser, 2);
        test.units(lodor, "space", naalu, UnitType.Destroyer, 2);
        test.units(lodor, "space", test.sol, UnitType.Dreadnought, 5);
        String foresight = "FFCC_naalu_retreat_" + position + "_foresight";
        AiPrompt start = prompt(
                "start",
                PromptSource.COMBAT_THREAD,
                NOW,
                foresight,
                "FFCC_naalu_combatRoll_" + position + "_space",
                "combatRoll_" + position + "_space");

        assertThat(decide(start)).hasValue(foresight);
    }

    @Test
    void declinesZeuOnAnotherPlayersToken() {
        AiPrompt offer = prompt(
                "zeu",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_naalu_exhaustAgent_naaluagent-te_" + position + "_sol",
                "deleteButtons");

        assertThat(decide(offer)).hasValue("deleteButtons");
    }

    @Test
    void playsTheOracleInTheStatusPhase() {
        naalu.unsafeGetLeader(OracleRules.HERO).setLocked(false);
        test.sol.setPromissoryNote("blue_ta");
        test.game.setPhaseOfGame("statusHomework");
        AiPrompt reminder = prompt("oracle", PromptSource.AI_THREAD, NOW, OracleRules.HERO_BUTTON, "deleteButtons");

        assertThat(decide(reminder)).hasValue(OracleRules.HERO_BUTTON);
    }

    @Test
    void deploysTheIconoclastWhenSomeoneGainsARelic() {
        AiPrompt offer = AiTestGame.withContent(
                prompt(
                        "icono",
                        PromptSource.PUBLIC,
                        NOW,
                        "FFCC_naalu_" + IconoclastRules.PLACE_PREFIX + "maaluuk",
                        "FFCC_naalu_" + IconoclastRules.PLACE_PREFIX + "druaa",
                        "deleteButtons"),
                "somebody just drew a relic. You may use the buttons to " + IconoclastRules.DEPLOY_TEXT + " onto");

        assertThat(decide(offer)).hasValueSatisfying(id -> assertThat(id).startsWith("FFCC_naalu_placeOneNDone"));
    }

    @Test
    void returnsCommittedFightersAfterTheGroundCombat() {
        naalu.addPlanet("lodor");
        test.units(lodor, "lodor", naalu, UnitType.Infantry, 1);
        test.units(lodor, "lodor", naalu, UnitType.Fighter, 2);
        AiPrompt back = prompt(
                "back", PromptSource.PUBLIC, NOW, MatriarchRules.RETURN_PREFIX + position, "FFCC_naalu_deleteButtons");

        assertThat(decide(back)).hasValue(MatriarchRules.RETURN_PREFIX + position);
    }

    // Naalu invades Sol's Lodor with two infantry and two fighters committed by the Matriarch. Sol's two hits must not
    // land on the infantry (the auto-assign order), or no ground force is left to take the planet.
    @Test
    void assignsGroundHitsToCommittedFightersFirst() {
        test.isActive(naalu, "action");
        test.game.setStoredValue("currentActionSummarynaalu", " Activated " + position + " (Lodor).");
        test.sol.addPlanet("lodor");
        test.units(lodor, "space", naalu, UnitType.Flagship, 1);
        test.units(lodor, "lodor", naalu, UnitType.Infantry, 2);
        test.units(lodor, "lodor", naalu, UnitType.Fighter, 2);
        test.units(lodor, "lodor", test.sol, UnitType.Infantry, 1);
        String byHand = MatriarchRules.BY_HAND.formatted(position);
        AiPrompt hits =
                prompt("hits", PromptSource.COMBAT_THREAD, NOW, "FFCC_naalu_autoAssignGroundHits_lodor_2", byHand);

        assertThat(decide(hits)).hasValue(byHand);

        test.game.setStoredValue("naalulatestAssignHits", "groundcombat");
        String infantry = "FFCC_naalu_assignHits_" + position + "_1_gf_lodor_green";
        String fighter = "FFCC_naalu_assignHits_" + position + "_1_ff_lodor_green";
        AiPrompt units = prompt("byHand", PromptSource.COMBAT_THREAD, NOW, infantry, fighter, "deleteButtons");

        assertThat(decide(units)).hasValue(fighter);
        assertThat(decide(units)).hasValue(fighter);
        assertThat(decide(units)).hasValue("deleteButtons");
    }
}
