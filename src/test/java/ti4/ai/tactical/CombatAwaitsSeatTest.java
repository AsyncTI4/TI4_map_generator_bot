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
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The combat summary keeps a seat's optional ability buttons (Munitions Reserves, Gravleash, War Funding) after the
// combat ends. In a 48-game batch the stall detector read those leftovers as "waiting on a combat" 67 times, and the
// fallback pressed Assign Hits on finished combats. A combat waits on a seat only while that combat is still live.
class CombatAwaitsSeatTest extends BaseTi4Test {

    private static final String SPACE = "space";

    private AiTestGame test;
    private Player letnev;
    private Tile tile;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        letnev = test.addSeat(AiTestGame.SECOND_AI_ID, "letnev", "green");
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        test.isActive(test.sol, "action");
        test.game.setActiveSystem(position);
    }

    private AiPrompt combatSummary() {
        return prompt(
                "summary",
                PromptSource.COMBAT_THREAD,
                NOW,
                "getDamageButtons_" + position + "_spacecombat",
                "FFCC_letnev_munitionsReserves",
                "FFCC_letnev_assignGravleash_" + position,
                "combatRoll_" + position + "_space");
    }

    private AiPrompt leftoverHitChoices() {
        return prompt(
                "hits",
                PromptSource.COMBAT_THREAD,
                NOW,
                "FFCC_letnev_assignHits_" + position + "_AllShips",
                "FFCC_letnev_assignHits_" + position + "_All",
                "deleteButtons");
    }

    private boolean awaits(AiPrompt prompt) {
        return CombatRules.awaitsSeat(test.game, letnev, List.of(prompt), prompt);
    }

    @Test
    void aLiveCombatWaitsOnTheSeat() {
        test.units(tile, SPACE, letnev, UnitType.Dreadnought, 2);
        test.units(tile, SPACE, test.sol, UnitType.Cruiser, 2);

        assertThat(awaits(combatSummary())).isTrue();
        assertThat(awaits(leftoverHitChoices())).isTrue();
    }

    @Test
    void aCombatTheSeatWonNoLongerWaitsOnItsAbilityButtons() {
        test.units(tile, SPACE, letnev, UnitType.Dreadnought, 2);

        assertThat(awaits(combatSummary())).isFalse();
        assertThat(awaits(leftoverHitChoices())).isFalse();
    }

    @Test
    void aCombatTheSeatLostNoLongerWaitsOnIt() {
        test.units(tile, SPACE, test.sol, UnitType.Cruiser, 2);

        assertThat(awaits(combatSummary())).isFalse();
        assertThat(awaits(leftoverHitChoices())).isFalse();
    }
}
