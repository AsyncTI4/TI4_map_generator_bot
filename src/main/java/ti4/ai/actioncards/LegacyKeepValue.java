package ti4.ai.actioncards;

import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class LegacyKeepValue {

    private static final double NEVER_USED = 0.0;
    private static final double UNKNOWN = 0.5;
    private static final double LATER_STAGE = 1.0;
    private static final double SABOTAGE = 1.5;
    private static final Set<String> USELESS = Set.of(
            "intercept",
            "rout",
            "waylay",
            "scramble",
            "reflective",
            "bunker",
            "disable",
            "lost_star",
            "nav_suite",
            "silence_space",
            "solar_flare",
            "master_plan",
            "ghost_squad",
            "bribery",
            "deadly_plot",
            "confounding",
            "confusing",
            "fire_team",
            "courageous",
            "blackmarketdealing",
            "exchangeprogram",
            "lieinwait",
            "crisis",
            "puppetsonastring",
            "coup",
            "disgrace");
    private static final Map<String, Double> STAGE_ONE_KEEP = Map.of(
            ActionCardValue.MINING, 3.0,
            ActionCardValue.INDUSTRIAL, 3.0,
            ActionCardValue.SUMMIT, 3.0,
            ActionCardValue.ECONOMIC, 2.0,
            ActionCardValue.MESSIAH, 2.0,
            ActionCardValue.FRONTLINE, 2.0,
            ActionCardValue.WAR_EFFORT, 2.0);

    static double keepValue(Game game, Player seat, String alias) {
        if (alias.startsWith("sabo")) return SABOTAGE;
        if (USELESS.contains(alias) || alias.startsWith("dh")) return NEVER_USED;
        Double stageOne = STAGE_ONE_KEEP.get(alias);
        if (stageOne != null) {
            return Math.max(
                    stageOne, Math.min(ActionCardValue.playValue(game, seat, alias), ActionCardValue.ECONOMY_CAP));
        }
        if (alias.startsWith("mb") || alias.startsWith("sh") || alias.contains("rider") || alias.startsWith("veto")) {
            return LATER_STAGE;
        }
        return UNKNOWN;
    }
}
