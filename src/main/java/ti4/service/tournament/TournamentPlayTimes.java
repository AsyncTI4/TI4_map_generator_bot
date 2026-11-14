package ti4.service.tournament;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import ti4.discord.interactions.buttons.Buttons;
import ti4.settings.users.UserSettings;

@UtilityClass
public class TournamentPlayTimes {

    public static final String ZONE_SELECT = "trnTz_";
    public static final String PARTS_SELECT = "trnParts_";
    public static final String OPEN = "trnPlayTimes_";
    public static final String CONFIRM_JOIN = "trnJoinConfirm_";
    public static final String JOINING_SUFFIX = "_j";

    private static final Map<Integer, String> PLACES = places();

    public enum DayPart {
        EARLY_MORNING("Early morning", 5),
        MORNING("Morning", 8),
        LUNCH("Lunch", 11),
        AFTERNOON("Afternoon", 14),
        EVENING("Evening", 17),
        LATE_EVENING("Late evening", 20),
        NIGHT("Night", 23),
        LATE_NIGHT("Late night", 2);

        static final int LENGTH_HOURS = 3;

        public final String label;
        public final int startLocalHour;

        DayPart(String label, int startLocalHour) {
            this.label = label;
            this.startLocalHour = startLocalHour;
        }

        String range() {
            return String.format("%02d–%02d", startLocalHour, (startLocalHour + LENGTH_HOURS) % 24);
        }

        List<Integer> localHours() {
            return IntStream.range(startLocalHour, startLocalHour + LENGTH_HOURS)
                    .map(hour -> hour % 24)
                    .boxed()
                    .toList();
        }

        static DayPart byName(String name) {
            for (DayPart part : values()) {
                if (part.name().equals(name)) return part;
            }
            return null;
        }
    }

    private static Map<Integer, String> places() {
        Map<Integer, String> places = new LinkedHashMap<>();
        places.put(-600, "Hawaii");
        places.put(-540, "Alaska");
        places.put(-480, "US Pacific");
        places.put(-420, "US Mountain");
        places.put(-360, "US Central, Mexico City");
        places.put(-300, "US Eastern, Toronto");
        places.put(-240, "Atlantic, Santiago");
        places.put(-180, "Brazil, Buenos Aires");
        places.put(-120, "South Georgia");
        places.put(-60, "Azores, Cape Verde");
        places.put(0, "UK, Ireland, Portugal");
        places.put(60, "Central Europe");
        places.put(120, "Eastern Europe, South Africa");
        places.put(180, "Turkey, Moscow");
        places.put(240, "Dubai");
        places.put(300, "Pakistan");
        places.put(330, "India");
        places.put(360, "Bangladesh");
        places.put(420, "Thailand, Vietnam, Jakarta");
        places.put(480, "China, Singapore, Perth");
        places.put(540, "Japan, Korea");
        places.put(570, "Adelaide, Darwin");
        places.put(600, "Sydney, Melbourne");
        places.put(660, "Solomon Islands");
        places.put(720, "New Zealand");
        return places;
    }

    public static List<Integer> offsets() {
        return List.copyOf(PLACES.keySet());
    }

    public static boolean isKnown(int offsetMinutes) {
        return PLACES.containsKey(offsetMinutes);
    }

    public static String label(int offsetMinutes) {
        int hours = Math.abs(offsetMinutes) / 60;
        int minutes = Math.abs(offsetMinutes) % 60;
        String sign = offsetMinutes < 0 ? "-" : "+";
        return "UTC" + (offsetMinutes == 0 ? "" : sign + hours + (minutes == 0 ? "" : ":" + minutes));
    }

    public static String describe(int offsetMinutes) {
        return label(offsetMinutes) + " · " + PLACES.getOrDefault(offsetMinutes, "");
    }

    public static List<DayPart> parts(Collection<String> names) {
        if (names == null) return List.of();
        return names.stream().map(DayPart::byName).filter(Objects::nonNull).toList();
    }

    public static boolean hasPlayTimes(UserSettings settings) {
        return settings != null
                && settings.getUtcOffsetMinutes() != null
                && !parts(settings.getPlayTimeParts()).isEmpty();
    }

    public static Set<Integer> utcHours(UserSettings settings) {
        if (!hasPlayTimes(settings)) return Set.of();
        List<Integer> local = parts(settings.getPlayTimeParts()).stream()
                .flatMap(part -> part.localHours().stream())
                .toList();
        return toUtc(settings.getUtcOffsetMinutes(), local);
    }

    static Set<Integer> toUtc(int utcOffsetMinutes, Collection<Integer> localHours) {
        Set<Integer> hours = new LinkedHashSet<>();
        for (int local : localHours) {
            hours.add(Math.floorMod(Math.floorDiv(local * 60 - utcOffsetMinutes, 60), 24));
        }
        return hours;
    }

    public static String summary(UserSettings settings) {
        String zone = settings == null || settings.getUtcOffsetMinutes() == null
                ? "no time zone yet"
                : describe(settings.getUtcOffsetMinutes());
        List<DayPart> parts = settings == null ? List.of() : parts(settings.getPlayTimeParts());
        String times = parts.isEmpty()
                ? "no play times yet"
                : String.join(", ", parts.stream().map(part -> part.label).toList());
        return "**Time zone:** " + zone + "\n**Usually plays:** " + times;
    }

    public static String pickerText(UserSettings settings, boolean joining) {
        String intro = joining
                ? "Before you join, tell us when you usually play, in your own time. Groups are drawn so players"
                        + " share hours. Pick your time zone and every part of the day you play, then press"
                        + " **Confirm and join**."
                : "When do you usually play, in your own time? Groups are drawn so players share hours. Changes are"
                        + " saved right away and kept for future tournaments.";
        return intro + "\n\n" + summary(settings) + usedFirstNote(settings);
    }

    private static String usedFirstNote(UserSettings settings) {
        return switch (TournamentHours.profile(settings).source()) {
            case ACTIVITY ->
                "\n-# The bot already has your recorded activity from games, which is used first. Play times are"
                        + " kept as a backup.";
            case AFK_SETTINGS -> "\n-# Your AFK settings are used first. Play times are kept as a backup.";
            default -> "";
        };
    }

    public static List<ActionRow> picker(long tournamentId, UserSettings settings, boolean joining) {
        String suffix = tournamentId + (joining ? JOINING_SUFFIX : "");
        Integer offset = settings == null ? null : settings.getUtcOffsetMinutes();
        List<String> chosen =
                settings == null || settings.getPlayTimeParts() == null ? List.of() : settings.getPlayTimeParts();
        List<SelectOption> zones = PLACES.keySet().stream()
                .map(zone ->
                        SelectOption.of(describe(zone), String.valueOf(zone)).withDefault(Objects.equals(zone, offset)))
                .toList();
        List<SelectOption> dayParts = new ArrayList<>();
        for (DayPart part : DayPart.values()) {
            dayParts.add(SelectOption.of(part.label + " (" + part.range() + ")", part.name())
                    .withDefault(chosen.contains(part.name())));
        }
        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(StringSelectMenu.create(ZONE_SELECT + suffix)
                .setPlaceholder("Your time zone")
                .addOptions(zones)
                .setRequiredRange(1, 1)
                .build()));
        rows.add(ActionRow.of(StringSelectMenu.create(PARTS_SELECT + suffix)
                .setPlaceholder("When you usually play (pick all that apply)")
                .addOptions(dayParts)
                .setRequiredRange(1, dayParts.size())
                .build()));
        if (joining) rows.add(ActionRow.of(Buttons.green(CONFIRM_JOIN + tournamentId, "Confirm and join")));
        return rows;
    }
}
