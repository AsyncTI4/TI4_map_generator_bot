package ti4.service.strategycard;

import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;

@UtilityClass
public class StrategyCardMessageService {

    public static Optional<GameMessage> getStrategyCardMessage(String gameName, int round, int sc) {
        String secondaryKey = getSecondaryKey(round, sc);
        return GameMessageManager.getAll(gameName, GameMessageType.STRATEGY_CARD).stream()
                .filter(message -> Objects.equals(message.secondaryKey(), secondaryKey))
                .findFirst();
    }

    public static void replaceStrategyCardMessage(String gameName, String messageId, int round, int sc, long gameSaveTime) {
        getStrategyCardMessage(gameName, round, sc)
                .ifPresent(message -> GameMessageManager.remove(gameName, message.messageId()));
        GameMessageManager.add(gameName, messageId, GameMessageType.STRATEGY_CARD, gameSaveTime, getSecondaryKey(round, sc));
    }

    public static String getSecondaryKey(int round, int sc) {
        return round + "::" + sc;
    }
}
