package ti4.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class TestBedPreset {

    public static final List<String> START_PHASES = List.of("setup", "strategy", "action");

    private String name;
    private String description;
    private Boolean fog;
    private String mapString;
    private Seat you;
    private List<Seat> seats = new ArrayList<>();
    private Seat defaults;
    private String start = "setup";
    private List<String> combat = new ArrayList<>();
    private List<TestBedScript.Shortcut> shortcuts = new ArrayList<>();

    public List<Seat> allSeats() {
        List<Seat> all = new ArrayList<>();
        if (you != null) all.add(you);
        all.addAll(seats);
        return all;
    }

    @Data
    public static class Seat {
        private String faction;
        private String color;
        private String home;
        private boolean speaker;
        private Integer sc;
        private CardPick acs;
        private CardPick sos;
        private CardPick relics;
        private List<String> techs;
        private Integer tg;
        private Integer commodities;
        private String ccs;
        private Leaders leaders;
        private Map<String, String> units = new LinkedHashMap<>();
        private List<String> planets;

        public boolean hasRandomFaction() {
            return faction == null || faction.isBlank() || "random".equalsIgnoreCase(faction);
        }

        public boolean hasIdentity() {
            return faction != null || color != null || home != null || speaker || sc != null;
        }
    }

    @Data
    public static class Leaders {
        private List<String> unlock = new ArrayList<>();
        private List<String> exhaust = new ArrayList<>();
    }

    public record CardPick(List<String> ids, int random) {

        @JsonValue
        public Object toJson() {
            if (ids.isEmpty()) return random;
            List<Object> entries = new ArrayList<>(ids);
            if (random > 0) entries.add(random);
            return entries;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static CardPick from(Object raw) {
            if (raw instanceof Number count) return new CardPick(List.of(), count.intValue());
            if (raw instanceof String id) return new CardPick(List.of(id), 0);
            if (!(raw instanceof List<?> entries)) {
                throw new IllegalArgumentException("Expected a number, an id or a list of ids and numbers: " + raw);
            }
            List<String> ids = new ArrayList<>();
            int random = 0;
            for (Object entry : entries) {
                if (entry instanceof Number count) {
                    random += count.intValue();
                } else {
                    ids.add(String.valueOf(entry));
                }
            }
            return new CardPick(ids, random);
        }
    }
}
