package ti4.ai.brain;

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

    public static StrategyCard of(Game game, int initiative) {
        String automation = game.getStrategyCardModelByInitiative(initiative)
                .map(StrategyCardModel::getBotSCAutomationID)
                .orElse("")
                .toLowerCase();
        for (StrategyCard card : values()) {
            if (card != OTHER && automation.contains(card.name().toLowerCase())) return card;
        }
        return OTHER;
    }
}
