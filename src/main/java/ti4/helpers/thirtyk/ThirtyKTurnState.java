package ti4.helpers.thirtyk;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKAttachmentHandler;
import ti4.discord.interactions.buttons.handlers.explore.thirtyk.ThirtyKFrontierExploreHandler;
import ti4.game.Game;

@UtilityClass
public class ThirtyKTurnState {

    public static void clearEndOfTurn(Game game) {
        if (!ThirtyKCards.inUse(game)) return;
        ThirtyKTransactions.clearTurnGrants(game);
        ThirtyKAcState.clearTurnState(game);
        ThirtyKRelicHooks.clearTechSupport(game);
        ThirtyKSecretHooks.clearTurnState(game);
        ThirtyKAttachmentHandler.clearCarbonUsed(game);
    }

    public static void clearActionPhase(Game game) {
        if (!ThirtyKCards.inUse(game)) return;
        clearEndOfTurn(game);
        ThirtyKTransactions.clearAllGrants(game);
        ThirtyKAcState.clearActionPhaseState(game);
        ThirtyKFrontierExploreHandler.clearWereLost(game);
    }

    public static void removeKeysStartingWith(Game game, String prefix) {
        game.getStoredValueMap().keySet().removeIf(key -> key.startsWith(prefix));
    }
}
