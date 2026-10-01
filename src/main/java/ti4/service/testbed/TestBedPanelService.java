package ti4.service.testbed;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.model.TestBedScript.Shortcut;

@UtilityClass
public class TestBedPanelService {

    public static final String PREFIX = "testbedPanel_";
    public static final String ACT_AS = PREFIX + "actAs_";
    public static final String ACT_AS_ME = "me";
    public static final String TOOL = PREFIX + "tool_";
    public static final String PHASE = PREFIX + "phase_";
    public static final String JSON_SHORTCUT = PREFIX + "shortcut_";
    public static final String JAVA_SHORTCUT = PREFIX + "shortcutJ_";

    public enum Tool {
        tg("+1 TG"),
        comm("+1 Commodity"),
        tactic("+1 Tactic CC"),
        strategy("+1 Strategy CC"),
        ready("Ready All"),
        active("Make Active Player"),
        cards("Cards Info"),
        shortcuts("Shortcuts"),
        refresh("Refresh");

        private final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    private static final List<String> PHASES = List.of("strategy", "action", "statusScoring", "agenda");
    private static final int BUTTONS_PER_ROW = 5;

    public static String content(Game game, @Nullable Player target, @Nullable String status) {
        StringBuilder sb =
                new StringBuilder("**Test bed panel: ").append(game.getName()).append("**\n");
        if (target == null) {
            sb.append("Acting as: **yourself** (not seated).");
        } else {
            sb.append("Acting as: **")
                    .append(target.getFaction())
                    .append("** (")
                    .append(target.getColor())
                    .append(") · TG ")
                    .append(target.getTg())
                    .append(" · comms ")
                    .append(target.getCommodities())
                    .append(" · CCs ")
                    .append(target.getCCRepresentation());
        }
        sb.append("\nTools act on that seat. A seat's own channel always acts as that seat.");
        if (status != null) sb.append("\n> ").append(status);
        return sb.toString();
    }

    public static List<ActionRow> components(Game game, @Nullable Player target) {
        List<Button> seatButtons = new ArrayList<>();
        for (Player seat : game.getRealPlayers()) {
            String label = seat.getFaction() + " (" + seat.getColor() + ")";
            String id = ACT_AS + seat.getFaction();
            seatButtons.add(seat == target ? Buttons.green(id, label) : Buttons.gray(id, label));
        }
        seatButtons.add(Buttons.blue(ACT_AS + ACT_AS_ME, "Me"));

        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < seatButtons.size(); i += BUTTONS_PER_ROW) {
            rows.add(ActionRow.of(seatButtons.subList(i, Math.min(i + BUTTONS_PER_ROW, seatButtons.size()))));
        }
        rows.add(ActionRow.of(toolButtons(Tool.tg, Tool.comm, Tool.tactic, Tool.strategy, Tool.ready)));
        rows.add(ActionRow.of(toolButtons(Tool.active, Tool.cards, Tool.shortcuts, Tool.refresh)));
        rows.add(ActionRow.of(PHASES.stream()
                .map(phase -> Buttons.red(PHASE + phase, "Start " + phase))
                .toList()));
        return rows;
    }

    public static List<ActionRow> shortcutComponents(Game game) {
        List<Button> buttons = new ArrayList<>();
        List<Shortcut> jsonShortcuts = TestBedShortcuts.load(game);
        for (int i = 0; i < jsonShortcuts.size(); i++) {
            buttons.add(Buttons.blue(JSON_SHORTCUT + i, jsonShortcuts.get(i).getLabel()));
        }
        for (TestBedShortcuts.JavaShortcut shortcut : TestBedShortcuts.JAVA_SHORTCUTS) {
            buttons.add(Buttons.gray(JAVA_SHORTCUT + shortcut.id(), shortcut.label()));
        }
        List<Button> shown = buttons.subList(0, Math.min(buttons.size(), BUTTONS_PER_ROW * BUTTONS_PER_ROW));
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < shown.size(); i += BUTTONS_PER_ROW) {
            rows.add(ActionRow.of(shown.subList(i, Math.min(i + BUTTONS_PER_ROW, shown.size()))));
        }
        return rows;
    }

    private static List<Button> toolButtons(Tool... tools) {
        List<Button> buttons = new ArrayList<>();
        for (Tool tool : tools) buttons.add(Buttons.gray(TOOL + tool.name(), tool.label));
        return buttons;
    }

    public static String applyTool(Tool tool, Player target) {
        return switch (tool) {
            case tg -> {
                target.setTg(target.getTg() + 1);
                yield target.getFaction() + " now has " + target.getTg() + " TG.";
            }
            case comm -> {
                target.setCommodities(target.getCommodities() + 1);
                yield target.getFaction() + " now has " + target.getCommodities() + " commodities.";
            }
            case tactic -> {
                target.setTacticalCC(target.getTacticalCC() + 1);
                yield target.getFaction() + " CCs: " + target.getCCRepresentation();
            }
            case strategy -> {
                target.setStrategicCC(target.getStrategicCC() + 1);
                yield target.getFaction() + " CCs: " + target.getCCRepresentation();
            }
            case ready -> {
                target.getExhaustedPlanets().clear();
                target.getLeaders().forEach(TestBedPanelService::readyLeader);
                yield "Readied all planets and leaders of " + target.getFaction() + ".";
            }
            default -> throw new IllegalArgumentException("Tool " + tool + " needs the interaction event");
        };
    }

    private static void readyLeader(Leader leader) {
        leader.setExhausted(false);
    }
}
