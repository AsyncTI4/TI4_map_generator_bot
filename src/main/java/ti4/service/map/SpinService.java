package ti4.service.map;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.helpers.RandomHelper;
import ti4.helpers.SpinRingsHelper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;

/**
 * Using /spin commands, save spin settings and execute them manually or with
 * specific auto-triggers
 */
@UtilityClass
public class SpinService {
    public static final int MAX_RING_TO_SPIN = 12;

    private static final String SETTING_SEPARATOR = " ";
    private static final String OPTION_SEPARATOR = ":";
    private static final String LIST_SEPARATOR = ",";

    public enum Direction {
        CW("Clockwise"),
        CCW("Counter-Clockwise"),
        RND("Random");

        public final String displayName;

        Direction(String displayName) {
            this.displayName = displayName;
        }

        public static Direction fromString(String dir) {
            try {
                return Direction.valueOf(dir.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }
    }

    public enum AutoTrigger {
        STATUS("in Status Cleanup"),
        STRATEGY("start of Strategy"),
        NO("Never");

        private final String description;

        AutoTrigger(String description) {
            this.description = description;
        }

        public static AutoTrigger fromString(String trigger) {
            try {
                return AutoTrigger.valueOf(trigger.toUpperCase());
            } catch (Exception e) {
                return null;
            }
        }

        public static List<String> valuesAsStringList() {
            return List.of(values()).stream().map(v -> v.toString()).collect(Collectors.toList());
        }
    }

    public class SpinSetting {
        private String id;
        private String center;
        private List<Integer> ring;
        private Direction direction;
        private List<Integer> steps;
        private AutoTrigger trigger;

        public SpinSetting(
                String center,
                List<Integer> ring,
                Direction direction,
                List<Integer> steps,
                AutoTrigger trigger,
                Game game) {
            this.center = center;
            this.ring = ring;
            this.direction = direction;
            this.steps = steps;
            this.trigger = trigger;

            if (game != null) {
                this.id = String.valueOf(
                        ((game.getName() + toString()).hashCode() & 0x7FFFFFFF) % 1_000); // positive 3-digit ID
            }
        }

        public static SpinSetting fromString(String spinString, Game game) {
            String[] parts = spinString.toUpperCase().split(OPTION_SEPARATOR);
            List<Integer> ring = null;
            List<Integer> steps = null;
            Direction direction = null;
            String center = "000";
            AutoTrigger trigger = AutoTrigger.STATUS;
            try {
                ring = List.of(parts[0].split(LIST_SEPARATOR)).stream()
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());
                direction = Direction.valueOf(parts[1]);
                steps = List.of(parts[2].split(LIST_SEPARATOR)).stream()
                        .map(Integer::parseInt)
                        .collect(Collectors.toList());

                if (parts.length > 3) {
                    center = parts[3];
                }

                if (parts.length > 4) {
                    trigger = null;
                    trigger = AutoTrigger.valueOf(parts[4]);
                }
            } catch (Exception e) {
                BotLogger.error(
                        new LogOrigin(game),
                        "Failed read spin setting " + spinString + ", " + Constants.solaxPing(),
                        e);
            }

            return new SpinSetting(center, ring, direction, steps, trigger, game);
        }

        // ring:direction:steps:center:trigger
        // 1,2:CW:1,2:000:STATUS = ring 1 or 2 clockwise 1 or 2 steps with center at 000 auto-triggering in status phase
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(ring.stream().map(String::valueOf).collect(Collectors.joining(LIST_SEPARATOR)));
            sb.append(OPTION_SEPARATOR);
            sb.append(direction.toString());
            sb.append(OPTION_SEPARATOR);
            sb.append(steps.stream().map(String::valueOf).collect(Collectors.joining(LIST_SEPARATOR)));
            sb.append(OPTION_SEPARATOR);
            sb.append(center);
            sb.append(OPTION_SEPARATOR);
            sb.append(trigger.toString());
            return sb.toString();
        }

        public String getRepresentation(boolean forExecution) {
            StringBuilder sb = new StringBuilder("⚙️ ");
            if (id != null) {
                sb.append("(").append(id).append(") ");
            }
            sb.append("Ring **");
            sb.append(ring.stream().map(String::valueOf).collect(Collectors.joining(" or ")));
            sb.append("** of **");
            sb.append(center);
            sb.append("** to **");
            sb.append(direction.displayName);
            sb.append("** direction by **");
            sb.append(steps.stream().map(String::valueOf).collect(Collectors.joining(" or ")));
            sb.append("** steps");
            if (!forExecution) {
                sb.append(" auto-triggering **");
                sb.append(trigger.description);
                sb.append("**");
            }
            sb.append(".");
            return sb.toString();
        }

        public int ring() {
            return ring.size() > 1 ? RandomHelper.pickRandomFromList(ring) : ring.get(0);
        }

        public int steps() {
            return steps.size() > 1 ? RandomHelper.pickRandomFromList(steps) : steps.get(0);
        }

        public Direction direction() {
            if (direction == Direction.RND) {
                return RandomHelper.pickRandomFromList(List.of(Direction.CW, Direction.CCW));
            }
            return direction;
        }

        public String center() {
            return center;
        }

        public String id() {
            return id;
        }

        public AutoTrigger trigger() {
            return trigger;
        }

        public int maxRing() {
            return Integer.parseInt(center) / 100
                    + ring.stream().max(Integer::compareTo).orElse(0);
        }

        public boolean isValid() {
            if (ring == null
                    || ring.isEmpty()
                    || steps == null
                    || steps.isEmpty()
                    || direction == null
                    || trigger == null
                    || !PositionMapper.isTilePositionValid(center)) {
                return false;
            }
            int smallestRing = ring.stream().min(Integer::compareTo).orElse(0);
            if (smallestRing < 1) {
                return false;
            }

            // Step counts must be less than tiles in smallest ring
            for (int step : steps) {
                if (step < 0 || step > smallestRing * 6 - 1) {
                    return false;
                }
            }

            // Max ring check
            if (maxRing() > MAX_RING_TO_SPIN) {
                return false;
            }
            return true;
        }
    }

    public static void listSpinSettings(GenericInteractionCreateEvent event, Game game) {
        StringBuilder sb = new StringBuilder("__Current Spin Settings__\n");

        if ("OFF".equals(game.getSpinMode())) {
            sb.append("Spin mode is OFF.");

        } else if ("ON".equals(game.getSpinMode())) {
            sb.append("Spin mode is ON (original Fin logic):\n");
            sb.append("-# ring 1 CW one step\n");
            sb.append("-# ring 2 CCW two steps\n");
            sb.append("-# ring 3 CW three steps (except 6p map HS positions)");

        } else {
            List<String> overTheBoundsWarning = new ArrayList<>();
            for (SpinSetting setting : getSpinSettings(game)) {
                sb.append(setting.getRepresentation(false)).append("\n");
                if (setting.maxRing() > 9) {
                    overTheBoundsWarning.add(setting.id);
                }
            }
            if (!overTheBoundsWarning.isEmpty()) {
                sb.append("-# ⚠️ Setting " + String.join(",", overTheBoundsWarning)
                        + " could cause tiles to spin over the map edge.");
            }
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    public static void addSpinSetting(Game game, SpinSetting toAdd) {
        String currentSettings = game.getSpinMode();
        if ("OFF".equals(currentSettings) || "ON".equals(currentSettings)) {
            currentSettings = "";
        }
        game.setSpinMode(currentSettings + (currentSettings.isEmpty() ? "" : SETTING_SEPARATOR) + toAdd.toString());
    }

    public static void setSpinSettings(Game game, List<SpinSetting> settings) {
        if (settings.isEmpty()) {
            game.setSpinMode("OFF");
        } else {
            game.setSpinMode(String.join(
                    SETTING_SEPARATOR, settings.stream().map(s -> s.toString()).collect(Collectors.toList())));
        }
    }

    public static void executeSpinSettings(Game game, List<SpinSetting> settingsToExecute) {
        // TODO Refactor and move spin logic to this service
        SpinRingsHelper.spinRingsCustom(game, settingsToExecute);
    }

    public static List<SpinSetting> getSpinSettings(Game game) {
        if (game.getSpinMode() == null
                || game.getSpinMode().isEmpty()
                || "ON".equals(game.getSpinMode())
                || "OFF".equals(game.getSpinMode())) {
            return List.of();
        }
        return List.of(game.getSpinMode().split(SETTING_SEPARATOR)).stream()
                .map(s -> SpinSetting.fromString(s, game))
                .collect(Collectors.toList());
    }

    public static void executeSpinsForTrigger(Game game, AutoTrigger trigger) {
        List<SpinSetting> settingsToExecute = new ArrayList<>();
        getSpinSettings(game).stream()
                .filter(setting -> trigger.equals(setting.trigger()))
                .forEach(settingsToExecute::add);
        if (!settingsToExecute.isEmpty()) {
            executeSpinSettings(game, settingsToExecute);
        }
    }
}
