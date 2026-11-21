package ti4.ai.yin;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;
import static ti4.ai.yin.YinTestBoard.SPACE;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Devotion: after a space combat round the Yin may destroy one of its own cruisers or destroyers to hit an enemy ship
// of its choice. The seat trades when its cheapest such ship buys more (win chance x stake plus the ships traded) than
// it costs, once both sides have rolled the round and assigned their hits.
class DevotionRulesTest extends BaseTi4Test {

    private YinTestBoard board;
    private String start;
    private String refresh;

    @BeforeEach
    void setUp() {
        board = new YinTestBoard();
        start = "FFCC_yin_startDevotion_" + board.position;
        refresh = "refreshViewOfSystem_" + board.position + "_yin_sol_space";
        board.rounds(board.yin, SPACE, 1);
        board.rounds(board.sol, SPACE, 1);
    }

    private AiPrompt general(String... ids) {
        return prompt("general", PromptSource.COMBAT_THREAD, NOW, ids);
    }

    private void closeFight() {
        board.units(SPACE, board.yin, UnitType.Destroyer, 2);
        board.units(SPACE, board.yin, UnitType.Cruiser, 2);
        board.units(SPACE, board.sol, UnitType.Carrier, 2);
        board.units(SPACE, board.sol, UnitType.Cruiser, 2);
    }

    @Test
    void tradesADestroyerAfterARoundOfACloseFight() {
        closeFight();

        assertThat(DevotionRules.next(board.context(NOW, general(start, refresh)))
                        .map(AiTestGame::pressedId))
                .contains(start);
    }

    @Test
    void keepsItsShipWhenOnlyAFighterIsLeftToHit() {
        board.unlockCommander();
        board.units(SPACE, board.yin, UnitType.Destroyer, 1);
        board.units(SPACE, board.yin, UnitType.Dreadnought, 3);
        board.units(SPACE, board.sol, UnitType.Fighter, 1);

        assertThat(DevotionRules.next(board.context(NOW, general(start, refresh))))
                .isEmpty();
    }

    @Test
    void waitsForTheOpponentToFinishTheRound() {
        closeFight();
        board.rounds(board.sol, SPACE, 0);

        assertThat(DevotionRules.next(board.context(NOW, general(start, refresh))))
                .isEmpty();
    }

    @Test
    void waitsForHitsToBeAssigned() {
        closeFight();
        AiPrompt hits = prompt(
                "hits", PromptSource.COMBAT_THREAD, NOW, "FFCC_yin_autoAssignSpaceHits_" + board.position + "_2");

        assertThat(DevotionRules.next(board.context(NOW, general(start, refresh), hits)))
                .isEmpty();
    }

    @Test
    void neverInTheFirstRoundBeforeAnyDiceAreRolled() {
        closeFight();
        board.rounds(board.yin, SPACE, 0);
        board.rounds(board.sol, SPACE, 0);

        assertThat(DevotionRules.next(board.context(NOW, general(start, refresh))))
                .isEmpty();
    }

    // The bot deletes the Devotion button once it is used; the combat buttons' refresh posts it again for later rounds.
    @Test
    void bringsTheButtonBackWhenAnEarlierRoundUsedIt() {
        closeFight();
        AiTurnContext first = board.context(NOW, general(refresh));

        assertThat(DevotionRules.next(first).map(AiTestGame::pressedId)).contains(refresh);

        AiPrompt reposted = prompt("reposted", PromptSource.COMBAT_THREAD, NOW + 1000, start, refresh);
        assertThat(DevotionRules.next(board.context(NOW + 2000, reposted)).map(AiTestGame::pressedId))
                .contains(start);
    }

    @Test
    void sacrificesItsCheapestShip() {
        closeFight();
        DevotionRules.next(board.context(NOW, general(start, refresh)));
        String destroyer = "FFCC_yin_resolveDevote_" + board.position + "_destroyer_devote";
        String cruiser = "FFCC_yin_resolveDevote_" + board.position + "_cruiser_devote";
        AiPrompt choice = prompt("choice", PromptSource.COMBAT_THREAD, NOW + 1000, cruiser, destroyer);

        assertThat(DevotionRules.next(board.context(NOW + 2000, choice)).map(AiTestGame::pressedId))
                .contains(destroyer);
    }

    // Hitting a cruiser (a 7) does more to an even fight than hitting a carrier (a 9) that dies last anyway, so the
    // seat
    // aims at the ship that gains most rather than at the most expensive one.
    @Test
    void hitsTheShipThatGainsTheMost() {
        closeFight();
        DevotionRules.next(board.context(NOW, general(start, refresh)));
        String carrier = "FFCC_yin_hitOpponent_" + board.position + "_carrier_blue_";
        String cruiser = "FFCC_yin_hitOpponent_" + board.position + "_cruiser_blue_";
        AiPrompt targets = prompt("targets", PromptSource.COMBAT_THREAD, NOW + 1000, carrier, cruiser);

        assertThat(DevotionRules.next(board.context(NOW + 2000, targets)).map(AiTestGame::pressedId))
                .contains(cruiser);
    }

    @Test
    void usesItOncePerRound() {
        closeFight();
        DevotionRules.next(board.context(NOW, general(start, refresh)));

        assertThat(DevotionRules.next(board.context(NOW + 1000, general(start, refresh))))
                .isEmpty();
    }
}
