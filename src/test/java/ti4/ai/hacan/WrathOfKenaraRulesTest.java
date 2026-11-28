package ti4.ai.hacan;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The bot counts the dice a +1 would turn into hits and offers Wrath of Kenara for them, 1 trade good each. The AI
// seat is given the Hacan flagship.
class WrathOfKenaraRulesTest extends BaseTi4Test {

    private static final String CONTENT_SUFFIX =
            ", there are 3 dice eligible to produce an additional hit using the _Wrath of Kenara_ flagship ability.";

    private AiTestGame test;
    private String position;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setPhaseOfGame("action");
        test.nekroHome();
        test.nekro.getUnitsOwned().remove("nekro_flagship");
        test.nekro.getUnitsOwned().add("hacan_flagship");
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        Tile tile = test.place("26", position);
        test.units(tile, "space", test.nekro, UnitType.Flagship, 1);
        test.units(tile, "space", test.sol, UnitType.Destroyer, 2);
        test.nekro.setTg(5);
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private AiPrompt offer(int misses, int hitsSoFar) {
        int total = misses + hitsSoFar;
        List<String> ids = new java.util.ArrayList<>();
        if (hitsSoFar > 0) ids.add("hacanFlagship_" + position + "_" + total + "_0");
        ids.add("FFCC_nekro_hacanFlagship_" + position + "_" + (misses - 1) + "_" + (hitsSoFar + 1));
        ids.add("FFCC_nekro_hacanFlagship_" + position + "_0_" + total);
        if (hitsSoFar > 0) ids.add("hacanFlagshipFinish_" + position + "_" + hitsSoFar);
        ids.add("deleteButtons");
        AiPrompt prompt = prompt("kenara" + hitsSoFar, PromptSource.COMBAT_THREAD, NOW, ids, List.of());
        return AiTestGame.withContent(prompt, test.nekro.getRepresentation() + CONTENT_SUFFIX);
    }

    // Three near misses, but the two enemy destroyers can only take two hits: the seat buys exactly 2 hits, then
    // finishes so the hits are assigned.
    @Test
    void buysNoMoreHitsThanTheEnemyFleetCanTake() {
        assertThat(pressed(WrathOfKenaraRules.next(test.context(offer(3, 0)))))
                .isEqualTo("FFCC_nekro_hacanFlagship_" + position + "_2_1");
        assertThat(pressed(WrathOfKenaraRules.next(test.context(offer(2, 1)))))
                .isEqualTo("FFCC_nekro_hacanFlagship_" + position + "_1_2");

        AiPrompt finishOnly = AiTestGame.withContent(
                prompt(
                        "kenaraDone",
                        PromptSource.COMBAT_THREAD,
                        NOW,
                        "hacanFlagship_" + position + "_3_0",
                        "FFCC_nekro_hacanFlagship_" + position + "_0_3",
                        "hacanFlagshipFinish_" + position + "_2",
                        "deleteButtons"),
                test.nekro.getRepresentation() + CONTENT_SUFFIX);
        assertThat(pressed(WrathOfKenaraRules.next(test.context(finishOnly))))
                .isEqualTo("hacanFlagshipFinish_" + position + "_2");
    }

    // A sustaining dreadnought takes two hits, so all three near misses are worth a trade good.
    @Test
    void countsSustainDamageAsAHitTheEnemyCanTake() {
        test.units(test.game.getTileByPosition(position), "space", test.sol, UnitType.Dreadnought, 1);

        assertThat(pressed(WrathOfKenaraRules.next(test.context(offer(3, 0)))))
                .isEqualTo("FFCC_nekro_hacanFlagship_" + position + "_0_3");
    }

    // Negotiate Trade Routes needs 5 trade goods and the seat holds exactly 5: none are spent on dice.
    @Test
    void keepsTradeGoodsReservedForScoring() {
        test.game.getRevealedPublicObjectives().put("trade_routes", 1);

        assertThat(pressed(WrathOfKenaraRules.next(test.context(offer(3, 0))))).isEqualTo("deleteButtons");
    }
}
