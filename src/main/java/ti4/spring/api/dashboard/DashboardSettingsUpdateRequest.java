package ti4.spring.api.dashboard;

import java.util.List;

public record DashboardSettingsUpdateRequest(
        List<String> preferredColors,
        Integer personalPingInterval,
        Boolean prefersDistanceBasedTacticalActions,
        List<Integer> afkHours,
        Boolean pingOnNextTurn,
        Boolean showTransactables,
        Boolean prefersSarweenMsg,
        Boolean prefersPillageMsg,
        String voltronStyle,
        Boolean prefersAutoDebtClearance,
        Boolean activityTracking,
        Boolean prefersPassOnWhensAfters,
        Boolean prefersPrePassOnSC,
        Boolean prefersWrongButtonEphemeral,
        Integer autoNoSaboInterval,
        String whisperPref,
        String supportPref,
        String sandbagPref,
        String winmakingPref,
        String takebackPref,
        String metaPref) {}
