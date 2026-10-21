package ti4.ai.strategy;

import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.ActionCardPrior;
import ti4.ai.scoring.ObjectiveValue;

@UtilityClass
public class CardValueScale {

    public static final double VICTORY_POINT = ObjectiveValue.VICTORY_POINT_VALUE;
    public static final double TRADE_GOOD = 1.0;
    static final double COMMODITY = 0.5;
    public static final double TOKEN_FLOOR = 1.5;
    static final double TOKEN_ACTION_SHARE = 0.6;
    static final double STRATEGY_TOKEN = 1.0;
    static final double ACTION_CARD = ActionCardPrior.AVERAGE_CARD;
    static final double SPEAKER = 1.5;
    static final double SPEAKER_FOR_IMPERIAL = 1.5;
    static final double AGENDA_PEEK = 0.5;
    static final double DOCK_EARLY = 4.0;
    static final double DOCK_LATE = 2.0;
    static final int LAST_EARLY_DOCK_ROUND = 3;
    static final double PDS = 1.0;
    static final double THREATENED_PDS = 1.0;
    static final double FRONTIER_PDS = 0.2;
    static final double CONSTRUCTION_FOLLOW_MARGIN = 0.5;
    static final double STRUCTURE_OBJECTIVE_SHARE = 0.8;
    static final double PRODUCTION_SHARE = 0.8;
    static final double BUILD_THEN_MOVE_SHARE = 0.5;
    static final double FAR_REACH_SHARE = 0.5;
    static final double READY_SHARE = 0.5;
    static final double PEACE_ACCORDS_SHARE = 1.0;
    static final double PEACE_ACCORDS_PLAY_MIN = 2.0;
    static final double READY_SPEND_BONUS = 1.5;
    static final int READY_FORECAST_CAP = 6;
    static final double PROTECT_SHARE = 0.5;
    static final double MIN_LOCK_EXPOSURE = 1.0;
    static final double NEAR_WIN_MULTIPLIER = 1.5;
    static final int NEAR_WIN_GAP = 3;
    public static final double SPEND_UNLOCK_SHARE = 0.8;
    static final double MECATOL_THREATENED_SHARE = 0.75;
    static final double MECATOL_TAKE_SHARE = 0.5;
    static final double SPEAKER_RACE_SHARE = 0.2;
    static final double SPEAKER_RACE_RIVAL_NOW = 0.2;
    static final double LEADERSHIP_RACE_CAP = 0.5;
    static final double DENIAL_SHARE = 0.5;
    static final int DENIAL_CLOSE_GAP = 2;
    static final double DISTANT_DENIAL_SHARE = 0.5;
    static final double TRADE_LEVERAGE = 0.5;
    static final double PRIOR_WEIGHT = 1.0;
    static final double WARFARE_PLAY_MIN_GAIN = 2.0;
    static final double REPLAN_DELTA = 1.0;
    static final double SETUP_SHARE = 0.5;
    static final double OTHER_CARD = 3.0;
    static final double SPEAKER_HANDOFF_SHARE = -0.5;
    static final double SECOND_RESEARCH_SHARE = 0.5;
    static final double CROWDED_DOUBLE_BUILD_SHARE = 0.5;
    static final double TE_FIRST_BUILD_SHARE = 0.5;
    static final double EXPEDITION_FIRST_SHARE = 0.5;
    static final double EXPEDITION_PLAY_MIN = 0.5;
}
