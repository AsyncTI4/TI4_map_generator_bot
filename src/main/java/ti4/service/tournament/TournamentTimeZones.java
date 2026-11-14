package ti4.service.tournament;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

@UtilityClass
public class TournamentTimeZones {

    public static final String SELECT = "trnTz_";

    private static final Map<Integer, String> PLACES = places();

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

    public static StringSelectMenu select(long tournamentId) {
        List<SelectOption> options = PLACES.keySet().stream()
                .map(offset -> SelectOption.of(describe(offset), String.valueOf(offset)))
                .toList();
        return StringSelectMenu.create(SELECT + tournamentId)
                .setPlaceholder("Optional: set your time zone, it helps grouping")
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }
}
