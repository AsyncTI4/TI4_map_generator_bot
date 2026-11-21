package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;
import static ti4.ai.yin.YinTestBoard.PLANET;
import static ti4.ai.yin.YinTestBoard.SPACE;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Brother Milor: after a unit is destroyed in combat, the Yin exhausts him so that player places 2 fighters (for a
// ship)
// or 2 infantry (for a ground force). The seat remembers its units when the combat's buttons first show up, and uses
// the agent on its own losses while the fight is still on and the 2 units are worth at least a trade good to it.
class BrotherMilorRulesTest extends BaseTi4Test {

    private YinTestBoard board;
    private String offer;

    @BeforeEach
    void setUp() {
        board = new YinTestBoard();
        offer = "FFCC_yin_yinagent_" + board.position;
    }

    private AiPrompt general() {
        return prompt("general", PromptSource.COMBAT_THREAD, NOW, offer);
    }

    private void lose(String holder, UnitType type) {
        board.tile.removeUnit(holder, Units.getUnitKey(type, board.yin.getColor()), 1);
    }

    private void evenSpaceFight() {
        board.units(SPACE, board.yin, UnitType.Cruiser, 3);
        board.units(SPACE, board.sol, UnitType.Cruiser, 3);
    }

    @Test
    void remembersItsUnitsBeforeAnyLoss() {
        evenSpaceFight();

        assertThat(BrotherMilorRules.next(board.context(NOW, general()))).isEmpty();
    }

    @Test
    void replacesAShipLostInAnOpenSpaceCombatWithFighters() {
        evenSpaceFight();
        BrotherMilorRules.next(board.context(NOW, general()));
        lose(SPACE, UnitType.Cruiser);

        assertThat(BrotherMilorRules.next(board.context(NOW + 1000, general())).map(AiTestGame::pressedId))
                .contains(offer);

        AiPrompt owners = prompt(
                "owners",
                PromptSource.COMBAT_THREAD,
                NOW + 2000,
                "FFCC_yin_exhaustAgent_yinagent_" + board.position + "_sol",
                "FFCC_yin_exhaustAgent_yinagent_" + board.position + "_yin");
        assertThat(BrotherMilorRules.next(board.context(NOW + 3000, owners)).map(AiTestGame::pressedId))
                .contains("FFCC_yin_exhaustAgent_yinagent_" + board.position + "_yin");

        String fighters = "FFCC_yin_placeOneNDone_skipbuild_2ff_" + board.position;
        AiPrompt place = prompt(
                "place",
                PromptSource.COMBAT_THREAD,
                NOW + 4000,
                fighters,
                "FFCC_yin_placeOneNDone_skipbuild_2gf_lodor");
        assertThat(BrotherMilorRules.next(board.context(NOW + 5000, place)).map(AiTestGame::pressedId))
                .contains(fighters);
    }

    @Test
    void replacesAGroundForceWithInfantryOnThatPlanet() {
        board.sol.addPlanet(PLANET);
        board.units(PLANET, board.yin, UnitType.Infantry, 3);
        board.units(PLANET, board.sol, UnitType.Infantry, 3);
        BrotherMilorRules.next(board.context(NOW, general()));
        lose(PLANET, UnitType.Infantry);
        BrotherMilorRules.next(board.context(NOW + 1000, general()));
        AiPrompt owners = prompt(
                "owners",
                PromptSource.COMBAT_THREAD,
                NOW + 2000,
                "FFCC_yin_exhaustAgent_yinagent_" + board.position + "_yin");
        BrotherMilorRules.next(board.context(NOW + 3000, owners));

        String infantry = "FFCC_yin_placeOneNDone_skipbuild_2gf_" + PLANET;
        AiPrompt place = prompt(
                "place",
                PromptSource.COMBAT_THREAD,
                NOW + 4000,
                "FFCC_yin_placeOneNDone_skipbuild_2ff_" + board.position,
                infantry);
        assertThat(BrotherMilorRules.next(board.context(NOW + 5000, place)).map(AiTestGame::pressedId))
                .contains(infantry);
    }

    @Test
    void keepsTheAgentOnceTheEnemyIsGone() {
        evenSpaceFight();
        BrotherMilorRules.next(board.context(NOW, general()));
        lose(SPACE, UnitType.Cruiser);
        board.tile.removeUnit(SPACE, Units.getUnitKey(UnitType.Cruiser, board.sol.getColor()), 3);

        assertThat(BrotherMilorRules.next(board.context(NOW + 1000, general()))).isEmpty();
    }

    @Test
    void keepsTheAgentWhenItIsExhausted() {
        evenSpaceFight();
        board.yin.unsafeGetLeader(BrotherMilorRules.AGENT).setExhausted(true);
        BrotherMilorRules.next(board.context(NOW, general()));
        lose(SPACE, UnitType.Cruiser);

        assertThat(BrotherMilorRules.next(board.context(NOW + 1000, general()))).isEmpty();
    }

    // Any seat a Yin player hands Brother Milor to places the units instead of leaving the prompt hanging.
    @Test
    void anotherSeatPlacesTheUnitsItIsGiven() {
        String fighters = "FFCC_nekro_placeOneNDone_skipbuild_2ff_" + board.position;
        AiPrompt gift = AiTestGame.withContent(
                prompt("gift", PromptSource.COMBAT_THREAD, NOW, fighters),
                "Nekro, use buttons to resolve Brother Milor, the Yin agent.");

        assertThat(BrotherMilorRules.next(board.test.context(gift)).map(AiTestGame::pressedId))
                .contains(fighters);
    }
}
