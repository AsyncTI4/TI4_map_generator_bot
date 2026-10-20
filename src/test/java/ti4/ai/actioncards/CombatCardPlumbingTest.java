package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.nekro.NekroBrain;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

class CombatCardPlumbingTest extends BaseTi4Test {

    private CardTable table;
    private String position;
    private String key;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        position = CardTable.SOL_SPOT;
        Tile tile = table.system("26", position);
        table.ships(table.nekro, tile, UnitType.Dreadnought, 1);
        table.ships(table.sol, tile, UnitType.Cruiser, 1);
        table.game.setActiveSystem(position);
        key = PlayMemory.combatKey(position, "space", 0);
    }

    @AfterEach
    void clearProperty() {
        System.clearProperty(AiSettings.COMBAT_CARDS_PROPERTY);
    }

    private void store(Stage stage) {
        CardPlay play = new CardPlay(stage, NOW, NOW, "emergency", "", false, 0, "");
        table.test.memory.put(key, play.encode());
    }

    private AiPrompt roll() {
        return prompt("roll", PromptSource.COMBAT_THREAD, NOW, "combatRoll_" + position + "_space");
    }

    // The seat's next own roll must be its combat-round roll, so nothing else may happen while the card is in play.
    @Test
    void theSeatHoldsEveryStageOfAnActivePlay() {
        for (Stage stage : new Stage[] {Stage.PLAYED, Stage.WINDOW, Stage.RESOLVE}) {
            store(stage);

            assertThat(CombatCardRules.hold(table.test.context(roll())).orElseThrow())
                    .as(stage.name())
                    .isInstanceOf(AiDecision.Wait.class);
        }
    }

    @Test
    void theSeatDoesNotHoldAFinishedOrCanceledPlay() {
        for (Stage stage : new Stage[] {Stage.DONE, Stage.CANCELED}) {
            store(stage);

            assertThat(CombatCardRules.hold(table.test.context(roll())))
                    .as(stage.name())
                    .isEmpty();
        }
    }

    @Test
    void theBrainWaitsRatherThanRollingWhileACardIsInPlay() {
        table.game.setStoredValue("combatRoundTrackersol" + position + "space", "1");
        store(Stage.WINDOW);

        assertThat(new NekroBrain().decide(table.test.context(roll()))).isInstanceOf(AiDecision.Wait.class);
    }

    @Test
    void aPlayForACombatThatIsOverIsCanceled() {
        store(Stage.WINDOW);
        table.game.setActiveSystem("");

        ActionCardRules.continuePlay(table.test.context());

        assertThat(table.test.memory.get(key).flatMap(CardPlay::decode).map(CardPlay::stage))
                .contains(Stage.CANCELED);
    }

    @Test
    void combatPlaysAreAmongTheActiveKeys() {
        store(Stage.PLAYED);

        assertThat(PlayMemory.activeKeys(table.test.context())).contains(key);
    }

    @Test
    void combatCardsCanBeSwitchedOffWithASystemProperty() {
        assertThat(AiSettings.isCombatCardsEnabled()).isTrue();

        System.setProperty(AiSettings.COMBAT_CARDS_PROPERTY, "false");

        assertThat(AiSettings.isCombatCardsEnabled()).isFalse();
    }
}
