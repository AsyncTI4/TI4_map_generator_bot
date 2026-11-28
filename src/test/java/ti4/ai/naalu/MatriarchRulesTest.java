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
import ti4.ai.tactical.TacticalRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Matriarch lets fighters in its system be committed to an invasion as ground forces; the bot returns them to
// space with an unowned "Return Fighters to Space" button once the ground combat is over. The seat (the test game's
// "nekro" seat, given the flagship) invades Sol's Lodor with its flagship, three fighters and two landed infantry.
class MatriarchRulesTest extends BaseTi4Test {

    private static final String PLANET = "lodor";

    private AiTestGame test;
    private Tile tile;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.nekroHome();
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        Set<String> units = new HashSet<>(test.nekro.getUnitsOwned());
        units.add(MatriarchRules.FLAGSHIP);
        test.nekro.setUnitsOwned(units);
        test.sol.addPlanet(PLANET);
        test.units(tile, PLANET, test.sol, UnitType.Infantry, 2);
        test.units(tile, "space", test.nekro, UnitType.Flagship, 1);
        test.units(tile, "space", test.nekro, UnitType.Fighter, 3);
        test.units(tile, PLANET, test.nekro, UnitType.Infantry, 2);
        test.aiIsActive("action");
        test.game.setActiveSystem(position);
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + position + " (Lodor).");
    }

    private String land(int fighters) {
        return "FFCC_nekro_landUnits_" + position + "_" + fighters + "ff_" + PLANET + "_" + test.nekro.getColor();
    }

    private AiPrompt landing() {
        return prompt("landing", PromptSource.PUBLIC, NOW, "FFCC_nekro_doneLanding_" + position, land(1), land(2));
    }

    private String commit() {
        return MatriarchRules.commitFighters(test.context(landing()), landing(), tile)
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    @Test
    void commitsFightersToThePlanetItInvades() {
        assertThat(commit()).isEqualTo(land(2));
    }

    // Wired into the landing step: once the infantry is down, the fighters follow before "done landing".
    @Test
    void theLandingStepCommitsThemBeforeFinishing() {
        assertThat(TacticalRules.continueAction(test.context(landing())).map(AiTestGame::pressedId))
                .hasValue(land(2));
    }

    @Test
    void commitsTheLastFighterAlone() {
        tile.removeUnit("space", ti4.helpers.Units.getUnitKey(UnitType.Fighter, test.nekro.getColor()), 2);

        assertThat(commit()).isEqualTo(land(1));
    }

    @Test
    void needsTheFlagshipInTheSystem() {
        tile.removeUnit("space", ti4.helpers.Units.getUnitKey(UnitType.Flagship, test.nekro.getColor()), 1);

        assertThat(commit()).isEmpty();
    }

    @Test
    void needsTheMatriarchItself() {
        Set<String> units = new HashSet<>(test.nekro.getUnitsOwned());
        units.remove(MatriarchRules.FLAGSHIP);
        test.nekro.setUnitsOwned(units);

        assertThat(commit()).isEmpty();
    }

    // Without a ground combat the fighters would only sit on the planet: they stay in space.
    @Test
    void notOnAnUndefendedPlanet() {
        tile.removeUnit(PLANET, ti4.helpers.Units.getUnitKey(UnitType.Infantry, test.sol.getColor()), 2);

        assertThat(commit()).isEmpty();
    }

    // Fighters cannot take control of a planet, so they only join ground forces that landed.
    @Test
    void notWithoutItsOwnGroundForcesThere() {
        tile.removeUnit(PLANET, ti4.helpers.Units.getUnitKey(UnitType.Infantry, test.nekro.getColor()), 2);

        assertThat(commit()).isEmpty();
    }

    @Test
    void returnsTheFightersOnceTheGroundCombatIsOver() {
        tile.removeUnit(PLANET, ti4.helpers.Units.getUnitKey(UnitType.Infantry, test.sol.getColor()), 2);
        test.units(tile, PLANET, test.nekro, UnitType.Fighter, 2);
        AiPrompt back = prompt(
                "back", PromptSource.PUBLIC, NOW, MatriarchRules.RETURN_PREFIX + position, "FFCC_nekro_deleteButtons");

        assertThat(MatriarchRules.returnFighters(test.context(back)).map(AiTestGame::pressedId))
                .hasValue(MatriarchRules.RETURN_PREFIX + position);
    }

    @Test
    void keepsThemThereWhileTheCombatGoesOn() {
        test.units(tile, PLANET, test.nekro, UnitType.Fighter, 2);
        AiPrompt back = prompt("back", PromptSource.PUBLIC, NOW, MatriarchRules.RETURN_PREFIX + position);

        assertThat(MatriarchRules.returnFighters(test.context(back))).isEmpty();
    }

    @Test
    void ignoresTheButtonWithoutFightersOnAPlanet() {
        AiPrompt back = prompt("back", PromptSource.PUBLIC, NOW, MatriarchRules.RETURN_PREFIX + position);

        assertThat(MatriarchRules.returnFighters(test.context(back))).isEmpty();
    }

    private String autoAssign(int hits) {
        return "FFCC_nekro_autoAssignGroundHits_" + PLANET + "_" + hits;
    }

    private String byHand() {
        return MatriarchRules.BY_HAND.formatted(position);
    }

    private AiPrompt hits(int hits) {
        return prompt("hits", PromptSource.COMBAT_THREAD, NOW, autoAssign(hits), byHand());
    }

    private String pick(String action, int amount, String unit) {
        return "FFCC_nekro_" + action + "_" + position + "_" + amount + "_" + unit + "_" + PLANET + "_"
                + test.nekro.getColor();
    }

    // The bot's ground hit message as getDamageButtons_..._groundcombat sends it: per-unit buttons and a Done button.
    private AiPrompt byHandPrompt(String... picks) {
        String[] ids = java.util.Arrays.copyOf(picks, picks.length + 1);
        ids[picks.length] = "deleteButtons";
        return prompt("byHand", PromptSource.COMBAT_THREAD, NOW, ids);
    }

    private String continueHits(AiPrompt prompt) {
        return MatriarchRules.continueHits(test.context(prompt))
                .map(AiTestGame::pressedId)
                .orElse("");
    }

    private void openedByHand(int hits) {
        test.units(tile, PLANET, test.nekro, UnitType.Fighter, 2);
        assertThat(ti4.ai.tactical.CombatRules.next(test.context(hits(hits))).map(AiTestGame::pressedId))
                .hasValue(byHand());
        test.game.setStoredValue("nekrolatestAssignHits", "groundcombat");
    }

    // Auto-assign destroys infantry before fighters, but committed fighters cannot take control (Naalu ruling,
    // Matriarch I): with fighters on the planet the seat assigns its hits by hand.
    @Test
    void assignsGroundHitsByHandWithFightersCommitted() {
        openedByHand(2);
    }

    @Test
    void autoAssignsWithoutCommittedFighters() {
        assertThat(ti4.ai.tactical.CombatRules.next(test.context(hits(2))).map(AiTestGame::pressedId))
                .hasValue(autoAssign(2));
    }

    // Two hits land on the two fighters; the infantry that will take the planet survive, then Done closes it.
    @Test
    void fightersTakeTheHitsBeforeTheGroundForces() {
        openedByHand(2);
        AiPrompt units =
                byHandPrompt(pick("assignHits", 1, "gf"), pick("assignHits", 2, "gf"), pick("assignHits", 1, "ff"));

        assertThat(continueHits(units)).isEqualTo(pick("assignHits", 1, "ff"));
        assertThat(continueHits(units)).isEqualTo(pick("assignHits", 1, "ff"));
        assertThat(continueHits(units)).isEqualTo("deleteButtons");
    }

    // A third hit, once the fighters are gone, falls on the infantry.
    @Test
    void infantryAfterTheFighters() {
        openedByHand(3);
        AiPrompt withFighters = byHandPrompt(pick("assignHits", 1, "gf"), pick("assignHits", 1, "ff"));
        continueHits(withFighters);
        continueHits(withFighters);
        AiPrompt infantryLeft = byHandPrompt(pick("assignHits", 1, "gf"));

        assertThat(continueHits(infantryLeft)).isEqualTo(pick("assignHits", 1, "gf"));
        assertThat(continueHits(infantryLeft)).isEqualTo("deleteButtons");
    }

    // A mech's free Sustain Damage comes before any loss.
    @Test
    void sustainsAMechFirst() {
        openedByHand(1);
        AiPrompt units =
                byHandPrompt(pick("assignDamage", 1, "mf"), pick("assignHits", 1, "ff"), pick("assignHits", 1, "mf"));

        assertThat(continueHits(units)).isEqualTo(pick("assignDamage", 1, "mf"));
    }

    // Other manual assignments (no hand assignment of its own under way) are left to their own rules.
    @Test
    void leavesOtherAssignmentsAlone() {
        test.game.setStoredValue("nekrolatestAssignHits", "groundcombat");

        assertThat(continueHits(byHandPrompt(pick("assignHits", 1, "gf")))).isEmpty();
    }
}
