package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

// Munitions Reserves costs 2 trade goods for the next roll only, so pressing the button again before that roll must not
// charge another 2.
class MunitionsReservesButtonTest extends BaseTi4Test {

    private AiTestGame test;
    private Player letnev;
    private ButtonInteractionEvent event;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        letnev = test.addSeat(AiTestGame.SECOND_AI_ID, "letnev", "green");
        letnev.setTg(6);
        event = mock(ButtonInteractionEvent.class);
    }

    private void press() {
        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            ButtonHelperAbilities.munitionsReserves(event, test.game, letnev);
        }
    }

    @Test
    void thePaymentArmsTheNextRoll() {
        press();

        assertThat(letnev.getTg()).isEqualTo(4);
        assertThat(test.game.getStoredValue("munitionsReserves")).isEqualTo("letnev");
    }

    @Test
    void aSecondPressBeforeTheRollCostsNothing() {
        press();
        press();

        assertThat(letnev.getTg()).isEqualTo(4);
    }

    @Test
    void payingAgainIsFineOnceTheRollHasUsedIt() {
        press();
        test.game.setStoredValue("munitionsReserves", "");
        press();

        assertThat(letnev.getTg()).isEqualTo(2);
    }

    @Test
    void withoutTwoTradeGoodsNothingIsArmed() {
        letnev.setTg(1);

        press();

        assertThat(letnev.getTg()).isEqualTo(1);
        assertThat(test.game.getStoredValue("munitionsReserves")).isEmpty();
    }
}
