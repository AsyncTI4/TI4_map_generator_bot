package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.service.combat.SpaceCannonOffenceWindow;
import ti4.testUtils.BaseTi4Test;

// After movement the engine opens the Space Cannon Offense window (LRR tactical action step 3) to every player whose
// cannons cover the active system. The AI must not move on to combat, landing or production while a covering player
// has neither fired nor declined: in game aiplay1 the Hacan AI built three dreadnoughts before the Xxcha flagship
// fired, and the hits were then assigned to ships that did not exist at fire time.
class SpaceCannonWaitTest extends BaseTi4Test {

    private static final String XXCHA_AI_ID = "7100000555555555";
    private static final String CONCLUDE = "FFCC_nekro_doneWithTacticalAction";

    private AiTestGame test;
    private String target;
    private Tile activated;
    private Tile beside;
    private AiPrompt conclude;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        target = AiTestGame.neighbourOf(AiTestGame.HOME);
        activated = test.place("26", target);
        beside = test.place("25", neighbourBeyond(target));
        test.units(activated, "space", test.nekro, UnitType.Fighter, 2);
        test.aiIsActive("action");
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + target + " (Lodor).");
        test.game.setActiveSystem(target);
        conclude = prompt("conclude", PromptSource.PUBLIC, NOW, CONCLUDE);
    }

    private static String neighbourBeyond(String position) {
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    private Player coveringSeat(String userId) {
        Player xxcha = test.addSeat(userId, "xxcha", "green");
        test.units(beside, "space", xxcha, UnitType.Flagship, 1);
        return xxcha;
    }

    private AiDecision continueAt(long now) {
        return TacticalRules.continueAction(test.contextAt(now, conclude)).orElseThrow();
    }

    @Test
    void waitsWhileACoveringHumanHasNeitherFiredNorDeclined() {
        coveringSeat("200000000000000007");

        AiDecision first = continueAt(NOW);
        assertThat(first).isInstanceOf(AiDecision.Wait.class);
        assertThat(((AiDecision.Wait) first).untilMillis()).isEqualTo(NOW + SpaceCannonWait.HUMAN_GRACE_MILLIS);

        assertThat(continueAt(NOW + SpaceCannonWait.HUMAN_GRACE_MILLIS - 1)).isInstanceOf(AiDecision.Wait.class);
    }

    // Like the combat wait, it asks for help when the window never resolves instead of building past it.
    @Test
    void asksForHelpWhenTheWindowNeverResolves() {
        coveringSeat("200000000000000007");
        continueAt(NOW);

        AiDecision later = continueAt(NOW + SpaceCannonWait.HUMAN_GRACE_MILLIS);

        assertThat(later).isInstanceOf(AiDecision.Unsure.class);
        assertThat(((AiDecision.Unsure) later).prompt()).isEqualTo(conclude);
    }

    // The space cannon roll and the decline button both record the presser; either one closes the window for them.
    @Test
    void movesOnOnceTheCoveringPlayerHasFiredOrDeclined() {
        Player xxcha = coveringSeat("200000000000000007");
        assertThat(continueAt(NOW)).isInstanceOf(AiDecision.Wait.class);

        SpaceCannonOffenceWindow.resolve(test.game, xxcha, target);

        assertThat(pressedId(continueAt(NOW + 1000))).isEqualTo(CONCLUDE);
    }

    @Test
    void doesNotWaitWithoutCannonCoverage() {
        assertThat(pressedId(continueAt(NOW))).isEqualTo(CONCLUDE);
    }

    @Test
    void doesNotWaitWithoutShipsInTheActiveSystem() {
        coveringSeat("200000000000000007");
        activated.removeAllUnits(test.nekro.getColor());

        assertThat(pressedId(continueAt(NOW))).isEqualTo(CONCLUDE);
    }

    // The window opens only after movement, so before the post-movement buttons appear there is nothing to wait for.
    @Test
    void doesNotWaitBeforeMovementFinished() {
        coveringSeat("200000000000000007");

        assertThat(TacticalRules.continueAction(test.contextAt(NOW))).isEmpty();
    }

    // A resolution from an earlier activation of the same system by another player does not count.
    @Test
    void ignoresAResolutionFromAnotherPlayersActivation() {
        Player xxcha = coveringSeat("200000000000000007");
        test.isActive(test.sol, "action");
        SpaceCannonOffenceWindow.resolve(test.game, xxcha, target);
        test.aiIsActive("action");

        assertThat(continueAt(NOW)).isInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void activationClearsTheWindow() {
        Player xxcha = coveringSeat("200000000000000007");
        SpaceCannonOffenceWindow.resolve(test.game, xxcha, target);
        assertThat(SpaceCannonOffenceWindow.resolvedBy(test.game, target)).containsExactly("xxcha");

        SpaceCannonOffenceWindow.open(test.game);

        assertThat(SpaceCannonOffenceWindow.resolvedBy(test.game, target)).isEmpty();
    }

    // AI defenders answer within a tick, so the wait on them is short.
    @Test
    void waitsOnlyBrieflyForAnAiDefender() {
        coveringSeat(XXCHA_AI_ID);

        AiDecision first = continueAt(NOW);

        assertThat(((AiDecision.Wait) first).untilMillis()).isEqualTo(NOW + SpaceCannonWait.AI_GRACE_MILLIS);
    }

    // An AI defender with coverage but nothing to shoot at declines explicitly, so an attacker waiting on the window
    // can move on.
    @Test
    void anAiDefenderWithNothingToShootAtDeclines() {
        Player xxcha = coveringSeat(XXCHA_AI_ID);
        test.isActive(test.sol, "action");
        String fire = "combatRoll_" + target + "_space_spacecannonoffence";
        String decline = "declinePDS_" + activated.getTileID();
        AiPrompt window = prompt("window", PromptSource.PUBLIC, NOW, fire, decline);

        Optional<AiDecision> decision = CombatRules.next(test.contextFor(xxcha, Set.of(), NOW, window));

        assertThat(pressedId(decision.orElseThrow())).isEqualTo(decline);
    }

    @Test
    void anAiDefenderStillFiresAtShipsInTheSystem() {
        Player xxcha = coveringSeat(XXCHA_AI_ID);
        test.isActive(test.sol, "action");
        test.units(activated, "space", test.sol, UnitType.Cruiser, 1);
        String fire = "combatRoll_" + target + "_space_spacecannonoffence";
        AiPrompt window = prompt("window", PromptSource.PUBLIC, NOW, fire, "declinePDS_" + activated.getTileID());

        Optional<AiDecision> decision = CombatRules.next(test.contextFor(xxcha, Set.of(), NOW, window));

        assertThat(pressedId(decision.orElseThrow())).isEqualTo(fire);
    }
}
