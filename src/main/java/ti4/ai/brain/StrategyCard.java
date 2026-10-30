package ti4.ai.brain;

import java.util.Map;
import ti4.game.Game;
import ti4.model.StrategyCardModel;

public enum StrategyCard {
    LEADERSHIP,
    DIPLOMACY,
    POLITICS,
    CONSTRUCTION,
    TRADE,
    WARFARE,
    TECHNOLOGY,
    IMPERIAL,
    OTHER;

    private static final Map<String, StrategyCard> EXPLICIT_AUTOMATION = Map.of("base2", DIPLOMACY);

    public static StrategyCard of(Game game, int initiative) {
        String automation = game.getStrategyCardModelByInitiative(initiative)
                .map(StrategyCardModel::getBotSCAutomationID)
                .orElse("")
                .toLowerCase();
        StrategyCard explicit = EXPLICIT_AUTOMATION.get(automation);
        if (explicit != null) return explicit;
        for (StrategyCard card : values()) {
            if (card != OTHER && automation.contains(card.name().toLowerCase())) return card;
        }
        return OTHER;
    }
}
