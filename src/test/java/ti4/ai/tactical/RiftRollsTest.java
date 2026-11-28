package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.Prompts;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Execution of gravity-rift rolls (LRR 41.2). The engine never knows which path a ship took, so the AI records which
// ships it routed through a rift when it concludes the move, then rolls exactly those on the engine's rift prompt.
class RiftRollsTest extends BaseTi4Test {

    private AiTestGame test;
    private String rift;
    private String target;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        rift = AiTestGame.neighbourOf(AiTestGame.HOME);
        target = MapPositionMapper.getAdjacentTilePositions(rift).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .filter(position -> !MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME)
                        .contains(position))
                .findFirst()
                .orElseThrow();
        test.place("41", rift);
        test.place("26", target);
        test.aiIsActive("action");
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + target + " (Lodor).");
        test.game.setActiveSystem(target);
    }

    // A carrier (move 1) two systems out only arrives through the rift, so it is rolled for once and the prompt closed.
    @Test
    void rollsEachRiftingShipOnceAfterMoving() {
        moved(AiTestGame.HOME, UnitType.Carrier, 1, 0);
        conclude();

        List<String> pressed = new ArrayList<>();
        pressed.add(pressedId(next(postMovement()).orElseThrow()));
        AiPrompt rolls = rollPrompt("_1carrier");
        pressed.add(pressedId(next(rolls).orElseThrow()));
        pressed.add(pressedId(next(rolls).orElseThrow()));

        assertThat(pressed)
                .containsExactly(
                        "FFCC_nekro_getRiftButtons_" + target,
                        "FFCC_nekro_riftUnit_" + target + "_1carrier",
                        "doneRifting");
        assertThat(next(rolls)).isEmpty();
    }

    // The roll-everything button would also roll ships that never left the rift, so it is never pressed; a ship whose
    // button is gone is skipped.
    @Test
    void neverPressesRiftAllUnits() {
        moved(AiTestGame.HOME, UnitType.Carrier, 1, 0);
        conclude();
        next(postMovement());

        AiPrompt rolls = rollPrompt("_1dreadnought");
        String pressed = pressedId(next(rolls).orElseThrow());

        assertThat(pressed).isEqualTo("doneRifting");
    }

    // A cruiser (move 2) goes around through a calm system, so the engine's rift button is left alone.
    @Test
    void ignoresTheRiftButtonWhenTheRouteWasCalm() {
        String around = MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && !position.equals(rift))
                .filter(position ->
                        MapPositionMapper.getAdjacentTilePositions(target).contains(position))
                .findFirst()
                .orElse(null);
        if (around == null) {
            // No second common neighbour on this board: move into the rift itself, which never rolls (note I).
            test.game.setActiveSystem(rift);
            target = rift;
        } else {
            test.place("19", around);
        }
        moved(AiTestGame.HOME, UnitType.Cruiser, 1, 0);
        conclude();

        assertThat(test.memory.has(RiftRolls.QUEUE_KEY + test.context().turnKey()))
                .isFalse();
        assertThat(next(postMovement())).isEmpty();
    }

    @Test
    void isUnsureWhenRollsArePlannedButNoRiftButtonAppears() {
        moved(AiTestGame.HOME, UnitType.Carrier, 1, 0);
        conclude();

        AiPrompt other = prompt("other", PromptSource.PUBLIC, NOW + 10, "FFCC_nekro_doneWithTacticalAction");

        assertThat(next(other).orElseThrow()).isInstanceOf(AiDecision.Unsure.class);
    }

    // A dreadnought that moved damaged rolls as the damaged one.
    @Test
    void rollsDamagedShipsThatMovedDamaged() {
        moved(AiTestGame.HOME, UnitType.Dreadnought, 1, 1);
        conclude();
        next(postMovement());

        AiPrompt rolls = rollPrompt("_1dreadnoughtdamaged", "_1dreadnought");

        assertThat(pressedId(next(rolls).orElseThrow()))
                .isEqualTo("FFCC_nekro_riftUnit_" + target + "_1dreadnoughtdamaged");
    }

    // Three carriers leave the rift. A press that changes nothing on the board counts as no progress after two
    // repeats, so they roll as a pair and then alone rather than through one button three times.
    @Test
    void rollsIdenticalShipsInPairsSoNoButtonRepeatsThreeTimes() {
        test.nekro.setFleetCC(5);
        moved(AiTestGame.HOME, UnitType.Carrier, 3, 0);
        conclude();
        next(postMovement());

        AiPrompt rolls = rollPrompt("_1carrier", "_2carrier");
        List<String> pressed = new ArrayList<>();
        for (int step = 0; step < 3; step++) pressed.add(pressedId(next(rolls).orElseThrow()));

        assertThat(pressed)
                .containsExactly(
                        "FFCC_nekro_riftUnit_" + target + "_2carrier",
                        "FFCC_nekro_riftUnit_" + target + "_1carrier",
                        "doneRifting");
    }

    // A damaged dreadnought comes in calmly from beside the target while an intact one leaves the rift: the roll is
    // for the intact one, the ship that actually left the rift (LRR 41.2).
    @Test
    void theRiftRollGoesToTheShipThatLeftTheRift() {
        String beside = MapPositionMapper.getAdjacentTilePositions(target).stream()
                .filter(position -> !"x".equals(position) && !position.equals(rift))
                .filter(position -> !AiTestGame.HOME.equals(position))
                .findFirst()
                .orElseThrow();
        test.place("19", beside);
        moved(beside, UnitType.Dreadnought, 1, 1);
        moved(rift, UnitType.Dreadnought, 1, 0);
        conclude();
        next(postMovement());

        AiPrompt rolls = rollPrompt("_1dreadnoughtdamaged", "_1dreadnought");

        assertThat(pressedId(next(rolls).orElseThrow())).isEqualTo("FFCC_nekro_riftUnit_" + target + "_1dreadnought");
    }

    // Engine and AI disagreed about the path: the seat is unsure once, then drops the planned rolls so the rest of
    // the action is not handed to the fallback over and over.
    @Test
    void anUnsureRiftQueueIsDroppedSoTheActionCarriesOn() {
        moved(AiTestGame.HOME, UnitType.Carrier, 1, 0);
        conclude();
        AiPrompt other = prompt("other", PromptSource.PUBLIC, NOW + 10, "FFCC_nekro_doneWithTacticalAction");

        assertThat(next(other).orElseThrow()).isInstanceOf(AiDecision.Unsure.class);
        assertThat(next(other)).isEmpty();
    }

    // The rolls are their own brain rule, ahead of combat: ships lost to the rift never fight (LRR 41.2).
    @Test
    void theRiftRuleOpensTheRollsWhileTheActionIsUnderWay() {
        moved(AiTestGame.HOME, UnitType.Carrier, 1, 0);
        conclude();

        assertThat(pressedId(
                        TacticalRules.rollForRifts(test.context(postMovement())).orElseThrow()))
                .isEqualTo("FFCC_nekro_getRiftButtons_" + target);
    }

    private void moved(String origin, UnitType type, int count, int damaged) {
        Map<UnitKey, List<Integer>> units = test.game
                .getTacticalActionDisplacement()
                .computeIfAbsent(origin + "-space", ignored -> new HashMap<>());
        units.put(
                Units.getUnitKey(type, test.nekro.getColor()),
                new ArrayList<>(List.of(count - damaged, damaged, 0, 0)));
        Tile arrived = test.game.getTileByPosition(target);
        test.units(arrived, "space", test.nekro, type, count);
        if (damaged > 0) arrived.addUnitDamage("space", Units.getUnitKey(type, test.nekro.getColor()), damaged);
    }

    private void conclude() {
        AiPrompt list = prompt("list", PromptSource.PUBLIC, NOW, "FFCC_nekro_concludeMove_" + target);
        assertThat(pressedId(TacticalRules.continueAction(test.context(list)).orElseThrow()))
                .isEqualTo("FFCC_nekro_concludeMove_" + target);
    }

    private AiPrompt postMovement() {
        return prompt(
                "post",
                PromptSource.PUBLIC,
                NOW + 10,
                "FFCC_nekro_getRiftButtons_" + target,
                "FFCC_nekro_doneWithTacticalAction");
    }

    private AiPrompt rollPrompt(String... units) {
        List<String> ids = new ArrayList<>();
        for (String unit : units) ids.add("FFCC_nekro_riftUnit_" + target + unit);
        ids.add("FFCC_nekro_riftAllUnits_" + target);
        ids.add("getDamageButtons_" + target + "_remove");
        ids.add("doneRifting");
        return prompt("rolls", PromptSource.PUBLIC, NOW + 20, ids, List.of());
    }

    private Optional<AiDecision> next(AiPrompt... prompts) {
        return RiftRolls.next(
                test.context(prompts), Prompts.newestFirst(List.of(prompts)), test.game.getTileByPosition(target));
    }
}
