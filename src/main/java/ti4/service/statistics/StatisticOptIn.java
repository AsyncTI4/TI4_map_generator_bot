package ti4.service.statistics;

import lombok.Data;

@Data
public class StatisticOptIn {

    private String playerDiscordId;
    private boolean excludeFromAsyncStats;
    private boolean showWinRates;
    private boolean showTurnStats;
    private boolean showCombatStats;
    private boolean showVpStats;
    private boolean showFactionStats;
    private boolean showOpponents;
    private boolean showGames;
}
