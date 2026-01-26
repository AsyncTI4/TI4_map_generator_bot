package ti4.service.transaction;

import lombok.experimental.UtilityClass;
import ti4.helpers.Constants;
import ti4.map.Player;

@UtilityClass
public class SendDebtService {

    public static void sendDebt(Player sendingPlayer, Player receivingPlayer, int debtCountToSend) {
        sendDebt(sendingPlayer, receivingPlayer, debtCountToSend, Constants.DEBT_DEFAULT_POOL);
    }

    public static void sendDebt(Player sendingPlayer, Player receivingPlayer, int debtCountToSend, String pool) {
        String sendingPlayerColor = sendingPlayer.getColor();
        receivingPlayer.addDebtTokens(sendingPlayerColor, debtCountToSend, pool);
    }
}
