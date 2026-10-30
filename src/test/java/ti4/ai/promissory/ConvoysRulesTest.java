package ti4.ai.promissory;

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
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * Trade Convoys is an ACTION: playing it takes the seat's action for the turn. Nekro (the AI) holds Hacan's Convoys
 * in round 3; Sol sits two systems away, so Nekro can't trade with it in the action phase, while Hacan (Guild Ships)
 * trades with anyone.
 */
class ConvoysRulesTest extends BaseTi4Test {

    private static final String NEAR = "302";
    private static final String FAR = "304";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setVp(10);
        test.game.setRound(3);
        test.aiIsActive("action");
        Player hacan = test.addSeat("200000000000000002", "hacan", "red");
        hacan.addOwnedPromissoryNoteByID("convoys");
        test.nekro.setPromissoryNote("convoys", 5);
        destroyer(test.nekro, AiTestGame.HOME);
        destroyer(test.sol, FAR);
    }

    private void destroyer(Player player, String position) {
        Tile tile = test.game.getTileByPosition(position);
        if (tile == null) tile = test.place("46", position);
        test.units(tile, "space", player, UnitType.Destroyer, 1);
        test.game.setLastModifiedDate(test.game.getLastModifiedDate() + 1);
    }

    private AiPrompt turnStart() {
        return prompt(
                "turn",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_tacticalAction",
                "FFCC_nekro_componentAction",
                "FFCC_nekro_passForRound");
    }

    private AiTurnContext context(AiPrompt... prompts) {
        return test.contextFor(test.nekro, Set.of(), NOW, prompts);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    // One new partner (Sol) over the two rounds counted is worth 2: rather than pass, Nekro opens its component
    // actions and plays Convoys there, which uses up its action like any other component action.
    @Test
    void playsConvoysAsItsLastActionBeforePassing() {
        AiPrompt turn = turnStart();
        assertThat(ConvoysRules.value(test.game, test.nekro)).isEqualTo(2.0);

        assertThat(pressed(ConvoysRules.beforePassing(context(turn), java.util.List.of(turn))))
                .isEqualTo("FFCC_nekro_componentAction");

        // The bot posts the component action menu privately, in the seat's cards-info thread.
        AiPrompt menu = prompt(
                "menu",
                PromptSource.AI_THREAD,
                NOW,
                "FFCC_nekro_componentActionRes_relic_x",
                "FFCC_nekro_componentActionRes_pn_convoys");
        assertThat(pressed(ConvoysRules.next(context(menu)))).isEqualTo("FFCC_nekro_componentActionRes_pn_convoys");
        assertThat(ConvoysRules.next(context(menu))).isEmpty();
        assertThat(ConvoysRules.beforePassing(context(turn), java.util.List.of(turn)))
                .isEmpty();
    }

    // Once Sol is a neighbour there is nobody new to reach, so the note stays in hand.
    @Test
    void keepsItWhenEveryoneIsAlreadyReachable() {
        destroyer(test.sol, NEAR);
        AiPrompt turn = turnStart();

        assertThat(ConvoysRules.value(test.game, test.nekro)).isZero();
        assertThat(ConvoysRules.beforePassing(context(turn), java.util.List.of(turn)))
                .isEmpty();
    }

    // Hacan's own Convoys, or one already in the play area, is not Nekro's to play.
    @Test
    void playsOnlyAConvoysItHoldsInHand() {
        test.nekro.getPromissoryNotesInPlayArea().add("convoys");
        assertThat(ConvoysRules.heldConvoys(test.nekro)).isEmpty();

        test.nekro.getPromissoryNotesInPlayArea().clear();
        assertThat(ConvoysRules.heldConvoys(test.nekro)).contains("convoys");
    }
}
