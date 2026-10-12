package ti4.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.model.TestBedPreset.Seat;

@Data
public class TestBedScript {

    public static final List<String> VERBS = List.of("note", "press", "pressId", "do", "wait", "expect");
    public static final List<String> ACTIONS =
            List.of("startPhase", "setActivePlayer", "setStored", "removeStored", "runCron", "hand", "actAs");

    private String name;
    private String description;
    private String preset;
    private double settleSeconds = 0.5;
    private int timeoutSeconds = 20;
    private boolean stopOnFail;
    private List<Step> steps = new ArrayList<>();
    private List<Shortcut> shortcuts = new ArrayList<>();

    @Data
    public static class Step {
        private String label;
        private String note;
        private String as;
        private String in;
        private String press;
        private String pressId;

        @JsonProperty("do")
        private String action;

        private String key;
        private String value;
        private Seat hand;
        private Double wait;
        private Expect expect;
        private Double settleSeconds;
        private Integer timeoutSeconds;
        private Boolean stopOnFail;

        public List<String> verbs() {
            List<String> verbs = new ArrayList<>();
            if (note != null) verbs.add("note");
            if (press != null) verbs.add("press");
            if (pressId != null) verbs.add("pressId");
            if (action != null) verbs.add("do");
            if (wait != null) verbs.add("wait");
            if (expect != null) verbs.add("expect");
            return verbs;
        }

        public String describe() {
            if (label != null) return label;
            String actor = as == null ? "" : as + ": ";
            if (note != null) return note;
            if (press != null) return actor + "press `" + press + "`";
            if (pressId != null) return actor + "press id `" + pressId + "`";
            if (action != null) return actor + action + (value == null ? "" : " " + value);
            if (wait != null) return "wait " + wait + "s";
            if (expect != null) return "expect " + expect.describe();
            return "empty step";
        }
    }

    @Data
    public static class Expect {
        public static final String SINCE_START = "start";
        public static final String SINCE_STEP = "step";

        private String in;

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> contains = new ArrayList<>();

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> notContains = new ArrayList<>();

        private Integer count;
        private String ephemeral;
        private String modal;
        private boolean noFactionLeak;
        private String state;
        private String equals;
        private String since;
        private String matches;
        private String attachment;

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> buttons = new ArrayList<>();

        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        private List<String> noButtons = new ArrayList<>();

        public boolean sinceStart() {
            return SINCE_START.equals(since);
        }

        public String describe() {
            if (state != null) {
                if (equals != null) return "`" + state + "` = `" + equals + "`";
                String checks = contains.isEmpty() ? "" : " contains " + contains;
                return "`" + state + "`" + checks + (notContains.isEmpty() ? "" : " lacks " + notContains);
            }
            if (ephemeral != null) return "reply contains `" + ephemeral + "`";
            if (modal != null) return "modal `" + modal + "` opened";
            StringBuilder sb = new StringBuilder("in `").append(in).append('`');
            if (!contains.isEmpty()) sb.append(" contains ").append(contains);
            if (!notContains.isEmpty()) sb.append(" lacks ").append(notContains);
            if (count != null) sb.append(" ×").append(count);
            if (matches != null) sb.append(" matches /").append(matches).append('/');
            if (attachment != null)
                sb.append(" attachment `").append(attachment).append('`');
            if (!buttons.isEmpty()) sb.append(" buttons ").append(buttons);
            if (!noButtons.isEmpty()) sb.append(" no buttons ").append(noButtons);
            if (noFactionLeak) sb.append(" no faction leak");
            if (sinceStart()) sb.append(" since start");
            return sb.toString();
        }
    }

    @Data
    public static class Shortcut {
        private String label;
        private List<Step> steps = new ArrayList<>();
    }
}
