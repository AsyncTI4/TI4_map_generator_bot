package ti4.ai.sardakk;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.sardakk.SardakkTestBoard.FIRST_SOURCE_PLANET;
import static ti4.ai.sardakk.SardakkTestBoard.SECOND_SOURCE_PLANET;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalRules;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Commit Ground Forces step of a tactical action against Lodor, with G'hom Sek'kus unlocked and a stack on each
// of the two planets next door. The prompt is the one the engine posts: the landing buttons and the commander's.
class SardakkCommanderRulesTest extends BaseTi4Test {

    private static final String FIRST_PULL = "sardakkcommander_infantry_lodor_" + FIRST_SOURCE_PLANET;
    private static final String SECOND_PULL = "sardakkcommander_infantry_lodor_" + SECOND_SOURCE_PLANET;

    private SardakkTestBoard board;
    private AiTestGame test;

    @BeforeEach
    void setUp() {
        board = new SardakkTestBoard();
        test = board.test;
        board.unlockCommander();
        board.infantryOnEachSourcePlanet(3);
        test.aiIsActive("action");
        test.game.setActiveSystem(board.targetPosition());
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + board.targetPosition() + " (Lodor).");
    }

    private AiPrompt landingPrompt() {
        return AiTestGame.prompt(
                "landing",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_doneLanding_" + board.targetPosition(),
                FIRST_PULL,
                "sardakkcommander_mech_lodor_" + FIRST_SOURCE_PLANET,
                SECOND_PULL);
    }

    private void plan(int wantedOnLodor) {
        Map<String, Integer> landings = new LinkedHashMap<>();
        landings.put("lodor", wantedOnLodor);
        TacticalPlan plan = new TacticalPlan(Kind.ATTACK, board.targetPosition(), java.util.List.of(), landings, 9);
        TacticalRules.remember(test.context(), plan);
    }

    private Optional<AiDecision> next(Set<String> pressed, AiPrompt... prompts) {
        return SardakkCommanderRules.next(test.context(pressed, prompts));
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private static String keyOf(AiPrompt prompt, String customId) {
        return AiTurnContext.pressKey(
                prompt,
                prompt.buttons().stream()
                        .filter(button -> button.customId().equals(customId))
                        .findFirst()
                        .orElseThrow());
    }

    // Nothing is in orbit and the plan wants two forces on Lodor: one comes from each stack, and no stack is used twice
    // (the engine would happily offer the second infantry button of a planet that has already given one).
    @Test
    void takesOneForceFromEachPlanetForTheInvasion() {
        plan(2);
        AiPrompt prompt = landingPrompt();

        String first = pressed(next(Set.of(), prompt));
        assertThat(first).isIn(FIRST_PULL, SECOND_PULL);
        String second = pressed(next(Set.of(keyOf(prompt, first)), prompt));
        assertThat(second).isIn(FIRST_PULL, SECOND_PULL).isNotEqualTo(first);
        assertThat(next(Set.of(keyOf(prompt, first), keyOf(prompt, second)), prompt))
                .isEmpty();
    }

    @Test
    void neverUsesTheSameStackTwiceEvenIfTheButtonStaysOnScreen() {
        plan(3);
        AiPrompt prompt = landingPrompt();

        String first = pressed(next(Set.of(), prompt));
        AiPrompt stale = landingPrompt();

        String second = pressed(next(Set.of(), stale));

        assertThat(second).isIn(FIRST_PULL, SECOND_PULL).isNotEqualTo(first);
        assertThat(next(Set.of(), stale)).isEmpty();
    }

    // Two infantry already wait in orbit for the single planet the plan lands on: the commander is not needed.
    @Test
    void leavesTheCommanderAloneWhenTheShipsCarryEnough() {
        test.units(board.target, "space", test.nekro, UnitType.Infantry, 2);
        plan(2);

        assertThat(next(Set.of(), landingPrompt())).isEmpty();
    }

    @Test
    void fillsOnlyWhatTheShipsCannotCarry() {
        test.units(board.target, "space", test.nekro, UnitType.Infantry, 1);
        plan(2);

        assertThat(pressed(next(Set.of(), landingPrompt()))).isIn(FIRST_PULL, SECOND_PULL);
    }

    // An enemy destroyer sits over both stacks' system: they are under threat, so neither gives a force.
    @Test
    void doesNotStripAThreatenedPlanet() {
        test.units(board.source, "space", test.sol, UnitType.Destroyer, 1);
        plan(2);

        assertThat(next(Set.of(), landingPrompt())).isEmpty();
    }

    @Test
    void doesNotPullAcrossTheSeatsOwnCommandToken() {
        board.tokenOnSource();
        plan(2);

        assertThat(next(Set.of(), landingPrompt())).isEmpty();
    }

    @Test
    void waitsForTheSpaceCombatToFinish() {
        test.units(board.target, "space", test.nekro, UnitType.Carrier, 1);
        test.units(board.target, "space", test.sol, UnitType.Destroyer, 1);
        plan(2);

        assertThat(next(Set.of(), landingPrompt())).isEmpty();
    }

    @Test
    void doesNothingBeforeTheCommitStep() {
        plan(2);
        AiPrompt movement = AiTestGame.prompt("move", PromptSource.PUBLIC, NOW, "FFCC_nekro_concludeMove_301");

        assertThat(next(Set.of(), movement)).isEmpty();
    }

    @Test
    void doesNothingWhileTheCommanderIsLocked() {
        test.nekro.unsafeGetLeader("sardakkcommander").setLocked(true);
        plan(2);

        assertThat(next(Set.of(), landingPrompt())).isEmpty();
    }

    // Two infantry in orbit meet two defenders: roughly even. A third force from a stack next door is a big swing,
    // so the commander adds it.
    @Test
    void addsAForceWhenItTurnsAnEvenFightIntoAFavourableOne() {
        test.sol.addPlanet("lodor");
        test.units(board.target, "lodor", test.sol, UnitType.Infantry, 2);
        test.units(board.target, "space", test.nekro, UnitType.Infantry, 2);
        plan(2);

        assertThat(pressed(next(Set.of(), landingPrompt()))).isIn(FIRST_PULL, SECOND_PULL);
    }

    @Test
    void doesNotThrowForcesAtAnAlreadyWonFight() {
        test.sol.addPlanet("lodor");
        test.units(board.target, "lodor", test.sol, UnitType.Infantry, 1);
        test.units(board.target, "space", test.nekro, UnitType.Infantry, 5);
        plan(5);

        assertThat(next(Set.of(), landingPrompt())).isEmpty();
    }

    @Test
    void doesNotThrowForcesAtAHopelessFight() {
        test.sol.addPlanet("lodor");
        test.units(board.target, "lodor", test.sol, UnitType.Infantry, 6);
        test.units(board.target, "space", test.nekro, UnitType.Infantry, 1);
        plan(1);

        assertThat(next(Set.of(), landingPrompt())).isEmpty();
    }
}
