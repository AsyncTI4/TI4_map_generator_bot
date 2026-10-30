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
import ti4.ai.brain.AiTurnContext;
import ti4.ai.letnev.LetnevCombatRules;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The Letnev round-start rules run through the same hook as the combat cards, so they always happen before the seat's
// own roll of the round: pay for Munitions Reserves, give a ship the agent's die, and only then roll.
class LetnevRoundStartHookTest extends BaseTi4Test {

    private static final long AFTER_GRACE = NOW + CombatRules.ROLL_GRACE_MILLIS;

    private AiTestGame test;
    private Player letnev;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        letnev = test.addSeat(AiTestGame.SECOND_AI_ID, "letnev", "green");
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile tile = test.place("26", position);
        test.units(tile, "space", letnev, UnitType.Dreadnought, 3);
        test.units(tile, "space", test.sol, UnitType.Dreadnought, 3);
        test.isActive(letnev, "action");
        test.game.setActiveSystem(position);
        letnev.setTg(6);
        letnev.addLeader("letnevagent");
    }

    private AiPrompt combatStart(String... extraButtons) {
        String[] buttons = new String[extraButtons.length + 1];
        System.arraycopy(extraButtons, 0, buttons, 0, extraButtons.length);
        buttons[extraButtons.length] = "combatRoll_" + position + "_space";
        return prompt("start", PromptSource.COMBAT_THREAD, NOW, buttons);
    }

    private Optional<AiDecision> next(AiPrompt... prompts) {
        AiTurnContext context = test.contextFor(letnev, Set.of(), AFTER_GRACE, prompts);
        return CombatRules.next(context, LetnevCombatRules::atRoundStart);
    }

    @Test
    void usesTheAgentThenPaysForMunitionsThenRolls() {
        AiPrompt start = combatStart("FFCC_letnev_getAgentSelection_letnevagent", "FFCC_letnev_munitionsReserves");
        AiPrompt targets =
                prompt("targets", PromptSource.COMBAT_THREAD, AFTER_GRACE, "exhaustAgent_letnevagent_letnev");

        assertThat(pressedId(next(start).orElseThrow())).isEqualTo("FFCC_letnev_getAgentSelection_letnevagent");
        assertThat(next(start)).containsInstanceOf(AiDecision.Wait.class);
        assertThat(pressedId(next(start, targets).orElseThrow())).isEqualTo("exhaustAgent_letnevagent_letnev");
        letnev.getLeader("letnevagent").orElseThrow().setExhausted(true);
        assertThat(pressedId(next(start).orElseThrow())).isEqualTo("FFCC_letnev_munitionsReserves");
        assertThat(pressedId(next(start).orElseThrow())).isEqualTo("combatRoll_" + position + "_space");
    }

    @Test
    void rollsAtOnceWhenNothingIsWorthSpending() {
        letnev.setTg(0);
        letnev.getLeader("letnevagent").orElseThrow().setExhausted(true);
        AiPrompt start = combatStart("FFCC_letnev_getAgentSelection_letnevagent", "FFCC_letnev_munitionsReserves");

        assertThat(pressedId(next(start).orElseThrow())).isEqualTo("combatRoll_" + position + "_space");
    }
}
