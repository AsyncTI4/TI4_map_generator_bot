package ti4.spring.api.dashboard;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.helpers.AliasHandler;
import ti4.image.Mapper;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;

@RequiredArgsConstructor
@Service
class DashboardSettingsService {

    private static final String EMPTY_HOURLY_TRACKING = "0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0;0";
    private static final Set<Integer> ALLOWED_PERSONAL_PING_INTERVALS =
            Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 24, 48);
    private static final Set<String> ALLOWED_VOLTRON_STYLES = Set.of(
            "eyes",
            "arms",
            "link",
            "saiyan",
            "at_field",
            "nyan",
            "fancy",
            "royal",
            "baba",
            "minis",
            "lightning",
            "panther");
    private static final Set<String> ALLOWED_WHISPER_PREFS =
            Set.of("No Whispers", "Limited Whispers", "Unlimited Whispers", "No Preference", "No Prefeference");
    private static final Set<String> ALLOWED_SUPPORT_PREFS =
            Set.of("Purge Supports", "Ban Support Swaps", "Keep Default Rules", "No Preference");
    private static final Set<String> ALLOWED_TAKEBACK_PREFS =
            Set.of("Unanimous Agreement", "Majority Agreement", "3rd Party Arbitration", "No Preference");
    private static final Set<String> ALLOWED_WINMAKING_PREFS =
            Set.of("Might Win Make In Any Position", "May Winmake If Cannot Win", "Will Not Winmake", "No Preference");
    private static final Set<String> ALLOWED_META_PREFS =
            Set.of("Dislike Space Risk More", "Dislike Boat Float More", "No Strong Feelings", "No Preference");
    private static final Set<String> ALLOWED_SANDBAG_PREFS = Set.of("bot", "manual", "No Preference");

    DashboardSettingsResponse getSettings(String userId) {
        return DashboardSettingsResponse.from(UserSettingsManager.get(userId));
    }

    DashboardSettingsResponse updateSettings(String userId, DashboardSettingsUpdateRequest request) {
        UserSettings settings = UserSettingsManager.get(userId);

        if (request.preferredColors() != null) {
            settings.setPreferredColors(normalizePreferredColors(request.preferredColors()));
        }
        if (request.personalPingInterval() != null) {
            int interval = request.personalPingInterval();
            validate(ALLOWED_PERSONAL_PING_INTERVALS.contains(interval), "Unsupported personal ping interval.");
            settings.setPersonalPingInterval(interval);
        }
        if (request.prefersDistanceBasedTacticalActions() != null) {
            settings.setPrefersDistanceBasedTacticalActions(request.prefersDistanceBasedTacticalActions());
        }
        if (request.afkHours() != null) {
            settings.setAfkHours(normalizeAfkHours(request.afkHours()));
        }
        if (request.pingOnNextTurn() != null) {
            settings.setPingOnNextTurn(request.pingOnNextTurn());
        }
        if (request.showTransactables() != null) {
            settings.setShowTransactables(request.showTransactables());
        }
        if (request.prefersSarweenMsg() != null) {
            settings.setPrefersSarweenMsg(request.prefersSarweenMsg());
        }
        if (request.prefersPillageMsg() != null) {
            settings.setPrefersPillageMsg(request.prefersPillageMsg());
        }
        if (request.voltronStyle() != null) {
            String style = request.voltronStyle().trim();
            validate(ALLOWED_VOLTRON_STYLES.contains(style), "Unsupported voltron style.");
            settings.setVoltronStyle(style);
        }
        if (request.prefersAutoDebtClearance() != null) {
            settings.setPrefersAutoDebtClearance(request.prefersAutoDebtClearance());
        }
        if (request.activityTracking() != null) {
            settings.setActivityTracking(request.activityTracking());
            if (!request.activityTracking()) {
                settings.setActiveHours(EMPTY_HOURLY_TRACKING);
            }
        }
        if (request.prefersPassOnWhensAfters() != null) {
            settings.setPrefersPassOnWhensAfters(request.prefersPassOnWhensAfters());
        }
        if (request.prefersPrePassOnSC() != null) {
            settings.setPrefersPrePassOnSC(request.prefersPrePassOnSC());
        }
        if (request.prefersWrongButtonEphemeral() != null) {
            settings.setPrefersWrongButtonEphemeral(request.prefersWrongButtonEphemeral());
        }
        if (request.autoNoSaboInterval() != null) {
            validate(request.autoNoSaboInterval() >= 0, "Auto no-sabo interval cannot be negative.");
            settings.setAutoNoSaboInterval(request.autoNoSaboInterval());
        }
        if (request.whisperPref() != null) {
            settings.setWhisperPref(
                    normalizeAllowed(request.whisperPref(), ALLOWED_WHISPER_PREFS, "whisper preference"));
            settings.setHasAnsweredSurvey(true);
        }
        if (request.supportPref() != null) {
            settings.setSupportPref(
                    normalizeAllowed(request.supportPref(), ALLOWED_SUPPORT_PREFS, "support preference"));
            settings.setHasAnsweredSurvey(true);
        }
        if (request.sandbagPref() != null) {
            settings.setSandbagPref(
                    normalizeAllowed(request.sandbagPref(), ALLOWED_SANDBAG_PREFS, "secret scoring preference"));
        }
        if (request.winmakingPref() != null) {
            settings.setWinmakingPref(
                    normalizeAllowed(request.winmakingPref(), ALLOWED_WINMAKING_PREFS, "winmaking preference"));
            settings.setHasAnsweredSurvey(true);
        }
        if (request.takebackPref() != null) {
            settings.setTakebackPref(
                    normalizeAllowed(request.takebackPref(), ALLOWED_TAKEBACK_PREFS, "takeback preference"));
            settings.setHasAnsweredSurvey(true);
        }
        if (request.metaPref() != null) {
            settings.setMetaPref(normalizeAllowed(request.metaPref(), ALLOWED_META_PREFS, "meta preference"));
            settings.setHasAnsweredSurvey(true);
        }

        UserSettingsManager.save(settings);
        return DashboardSettingsResponse.from(settings);
    }

    private static List<String> normalizePreferredColors(List<String> preferredColors) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String preferredColor : preferredColors) {
            if (preferredColor == null || preferredColor.isBlank()) {
                continue;
            }
            String resolvedColor =
                    AliasHandler.resolveColor(preferredColor.trim().toLowerCase());
            if (Mapper.isValidColor(resolvedColor)) {
                normalized.add(resolvedColor);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeAfkHours(List<Integer> afkHours) {
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer afkHour : afkHours) {
            validate(afkHour != null, "AFK hours cannot contain null.");
            validate(afkHour >= 0 && afkHour < 24, "AFK hours must be between 0 and 23.");
            normalized.add(afkHour);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + ";" + right)
                .orElse(null);
    }

    private static String normalizeAllowed(String rawValue, Set<String> allowedValues, String fieldName) {
        String normalized = rawValue.trim();
        if ("No Prefeference".equals(normalized)) {
            normalized = "No Preference";
        }
        validate(allowedValues.contains(normalized), "Unsupported " + fieldName + ".");
        return normalized;
    }

    private static void validate(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
