package ti4.ai.scoring;

import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Player;

@UtilityClass
public class PromisedTradeGoods {

    public static final String TRADE_GOODS = "TGs";
    private static final String SENDING = "sending";
    private static final String RECEIVING = "_receiving";
    private static final String SEPARATOR = "_";
    private static final int MAX_DIGITS = 6;

    public static int of(Player seat) {
        return sent(seat, TRADE_GOODS, null);
    }

    public static int to(Player seat, Player receiver) {
        return sent(seat, TRADE_GOODS, receiver.getFaction());
    }

    public static int sent(Player seat, String itemType, @Nullable String receiverFaction) {
        String prefix = SENDING + seat.getFaction() + RECEIVING;
        int total = 0;
        for (String item : seat.getTransactionItems()) {
            if (!item.startsWith(prefix)) continue;
            String rest = item.substring(prefix.length());
            String receiver = StringUtils.substringBefore(rest, SEPARATOR);
            String typeAndAmount = StringUtils.substringAfter(rest, SEPARATOR);
            String amount = StringUtils.substringAfter(typeAndAmount, SEPARATOR);
            boolean matches = itemType.equals(StringUtils.substringBefore(typeAndAmount, SEPARATOR))
                    && (receiverFaction == null || receiverFaction.equals(receiver))
                    && StringUtils.isNumeric(amount)
                    && amount.length() <= MAX_DIGITS;
            if (matches) total += Integer.parseInt(amount);
        }
        return total;
    }
}
