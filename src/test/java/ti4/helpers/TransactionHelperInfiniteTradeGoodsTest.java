package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.testUtils.BaseTi4Test;

class TransactionHelperInfiniteTradeGoodsTest extends BaseTi4Test {

    private Game game;
    private Player letnev;
    private Player hacan;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("infinite-trade-goods-test");
        letnev = game.addPlayer("111", "letnev");
        letnev.setFaction(game, "letnev");
        letnev.setColor("black");
        hacan = game.addPlayer("222", "hacan");
        hacan.setFaction(game, "hacan");
        hacan.setColor("yellow");
        letnev.setInfiniteTg(true);
    }

    private void press(Player sender, String buttonID) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class, RETURNS_DEEP_STUBS);
        try (MockedStatic<MessageHelper> ignored = mockStatic(MessageHelper.class)) {
            TransactionHelper.send(event, sender, buttonID, game);
        }
    }

    @Test
    void tradingAwaySomeTradeGoodsLeavesTheSenderInfinite() {
        press(letnev, "send_TGs_hacan_4");

        assertThat(letnev.isInfiniteTg()).isTrue();
        assertThat(hacan.isInfiniteTg()).isFalse();
        assertThat(hacan.getTg()).isEqualTo(4);
    }

    @Test
    void sendingInfinityGivesTheReceiverInfiniteTradeGoods() {
        press(letnev, "send_InfiniteTGs_hacan_1");

        assertThat(letnev.isInfiniteTg()).isTrue();
        assertThat(hacan.isInfiniteTg()).isTrue();
    }

    @Test
    void onlyAPlayerWithInfiniteTradeGoodsCanSendInfinity() {
        letnev.setInfiniteTg(false);
        press(hacan, "send_InfiniteTGs_letnev_1");

        assertThat(letnev.isInfiniteTg()).isFalse();
    }
}
