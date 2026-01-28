package ti4.service.transaction;

import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.emoji.TI4Emoji;

@UtilityClass
public class SendDebtService {

    public static void sendDebt(Player sendingPlayer, Player receivingPlayer, int debtCountToSend) {
        sendDebt(sendingPlayer, receivingPlayer, debtCountToSend, Constants.DEBT_DEFAULT_POOL);
    }

    public static void sendDebt(Player sendingPlayer, Player receivingPlayer, int debtCountToSend, String pool) {
        String sendingPlayerColor = sendingPlayer.getColor();
        receivingPlayer.addDebtTokens(sendingPlayerColor, debtCountToSend, pool);

        Game game = sendingPlayer.getGame();
        String bankSource = game.getDebtPoolIcon(pool);
        if (bankSource == null && !Constants.DEBT_DEFAULT_POOL.equalsIgnoreCase(pool)) {
            bankSource = TI4Emoji.getRandomizedEmoji(0, null).toString();
            game.setDebtPoolIcon(pool, bankSource);
        }
    }
}
