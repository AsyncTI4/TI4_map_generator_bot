package ti4.ai.strategy;

import java.util.Set;
import ti4.game.Game;
import ti4.model.StrategyCardModel;

enum CardVariant {
    POK,
    THUNDERS_EDGE,
    BASE_GAME;

    private static final Set<String> THUNDERS_EDGE_AUTOMATION = Set.of("te6warfare", "te4construction");
    private static final Set<String> BASE_GAME_AUTOMATION = Set.of("base2");

    static CardVariant of(Game game, int initiative) {
        String automation = game.getStrategyCardModelByInitiative(initiative)
                .map(StrategyCardModel::getBotSCAutomationID)
                .orElse("")
                .toLowerCase();
        if (THUNDERS_EDGE_AUTOMATION.contains(automation)) return THUNDERS_EDGE;
        if (BASE_GAME_AUTOMATION.contains(automation)) return BASE_GAME;
        return POK;
    }
}
