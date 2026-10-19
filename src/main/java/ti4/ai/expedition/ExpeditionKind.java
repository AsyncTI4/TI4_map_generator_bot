package ti4.ai.expedition;

import java.util.Arrays;
import java.util.Optional;
import ti4.game.Game;

public enum ExpeditionKind {
    TECH_SKIP("techSkip"),
    TRADE_GOODS("tradeGoods"),
    FIVE_RES("fiveRes"),
    FIVE_INF("fiveInf"),
    SECRET("secret"),
    ACTION_CARDS("actionCards");

    public static final String BUTTON_PREFIX = "TEexpedition_";

    private final String id;

    ExpeditionKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String handler() {
        return BUTTON_PREFIX + id;
    }

    public boolean isClaimed(Game game) {
        return game.getExpeditions().getExpeditionFactions().get(id) != null;
    }

    public static Optional<ExpeditionKind> of(String id) {
        return Arrays.stream(values()).filter(kind -> kind.id.equals(id)).findFirst();
    }
}
