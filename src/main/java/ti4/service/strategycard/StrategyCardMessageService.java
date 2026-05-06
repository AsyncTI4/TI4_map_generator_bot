package ti4.service.strategycard;

import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;

@UtilityClass
public class StrategyCardMessageService {

    public static final String INFO_PLAYED_AT = "playedAt";
    public static final String INFO_ROUND = "round";
    public static final String INFO_SC = "sc";

    public static Optional<GameMessage> getStrategyCardMessage(String gameName, int round, int sc) {
        return GameMessageManager.getAll(gameName, GameMessageType.STRATEGY_CARD).stream()
                .filter(message -> isStrategyCardMessage(message, round, sc))
                .findFirst();
    }

    public static void replaceStrategyCardMessage(
            String gameName, String messageId, int round, int sc, long gameSaveTime, long playedAt) {
        getStrategyCardMessage(gameName, round, sc)
                .ifPresent(message -> GameMessageManager.remove(gameName, message.messageId()));
        GameMessageManager.add(
                gameName,
                messageId,
                GameMessageType.STRATEGY_CARD,
                gameSaveTime,
                Map.of(
                        INFO_ROUND,
                        Integer.toString(round),
                        INFO_SC,
                        Integer.toString(sc),
                        INFO_PLAYED_AT,
                        Long.toString(playedAt)));
    }

    private static boolean isStrategyCardMessage(GameMessage message, int round, int sc) {
        return message.type() == GameMessageType.STRATEGY_CARD
                && message.getInfoAsInt(INFO_ROUND) == round
                && message.getInfoAsInt(INFO_SC) == sc;
    }
}
