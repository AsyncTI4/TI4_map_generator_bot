package ti4.spring.api.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import ti4.settings.users.UserSettings;

public record DashboardSettingsResponse(
        String userId,
        String activeHours,
        boolean activityTracking,
        String afkHours,
        int autoNoSaboInterval,
        int gameLimit,
        boolean hasAnsweredSurvey,
        boolean hasIndicatedStatPreferences,
        boolean lockedFromCreatingGames,
        String metaPref,
        LocalDateTime myDateTime,
        int personalPingInterval,
        boolean pingOnNextTurn,
        List<String> preferredColors,
        boolean prefersAutoDebtClearance,
        boolean prefersDistanceBasedTacticalActions,
        boolean prefersPassOnWhensAfters,
        boolean prefersPillageMsg,
        boolean prefersPrePassOnSC,
        boolean prefersSarweenMsg,
        Boolean prefersWrongButtonEphemeral,
        String sandbagPref,
        boolean showTransactables,
        String supportPref,
        String takebackPref,
        String trackRecord,
        String voltronStyle,
        String whisperPref,
        String winmakingPref) {

    static DashboardSettingsResponse from(UserSettings settings) {
        return new DashboardSettingsResponse(
                settings.getUserId(),
                settings.getActiveHours(),
                settings.isActivityTracking(),
                settings.getAfkHours(),
                settings.getAutoNoSaboInterval(),
                settings.getGameLimit(),
                settings.isHasAnsweredSurvey(),
                settings.isHasIndicatedStatPreferences(),
                settings.isLockedFromCreatingGames(),
                settings.getMetaPref(),
                settings.getLockedFromCreatingGamesUntil(),
                settings.getPersonalPingInterval(),
                settings.isPingOnNextTurn(),
                settings.getPreferredColors(),
                settings.isPrefersAutoDebtClearance(),
                settings.isPrefersDistanceBasedTacticalActions(),
                settings.isPrefersPassOnWhensAfters(),
                settings.isPrefersPillageMsg(),
                settings.isPrefersPrePassOnSC(),
                settings.isPrefersSarweenMsg(),
                settings.getPrefersWrongButtonEphemeral(),
                settings.getSandbagPref(),
                settings.isShowTransactables(),
                settings.getSupportPref(),
                settings.getTakebackPref(),
                settings.getTrackRecord(),
                settings.getVoltronStyle(),
                settings.getWhisperPref(),
                settings.getWinmakingPref());
    }
}
