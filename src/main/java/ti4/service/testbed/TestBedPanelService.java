package ti4.service.testbed;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.testbed.TestBedShortcuts.ButtonGroup;
import ti4.service.testbed.TestBedShortcuts.TestBedButton;
import ti4.service.testbed.TestBedTurnButtons.TurnButtons;

@UtilityClass
public class TestBedPanelService {

    public static final String PREFIX = "testbedPanel_";
    public static final String ACT_AS = PREFIX + "actAs_";
    public static final String ACT_AS_ME = "me";
    public static final String ACT_AS_TURN = "turn";
    public static final String TOOL = PREFIX + "tool_";
    public static final String PAGE = PREFIX + "page_";
    public static final String RUN = PREFIX + "run_";
    public static final String BACK = PREFIX + "back";
    public static final String GROUP_SELECT = PREFIX + "group";
    public static final String TURN_PRESS = PREFIX + "turnPress_";
    public static final String TURN_ACT_AS = PREFIX + "turnActAs_";
    private static final int MAX_COMBAT_SEATS = 5;
    public static final int PAGE_SIZE = 15;

    public enum Tool {
        active("Make Active"),
        turn("Turn Buttons"),
        buttons("Test Buttons"),
        refresh("Refresh");

        private final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    private static final int BUTTONS_PER_ROW = 5;
    private static final int MAX_SELECT_OPTIONS = 25;
    private static final int MAX_OPTION_TEXT = 100;
    private static final int MAX_STATUS = 1500;

    public record PageRef(String groupKey, int number) {}

    public static String content(Game game, @Nullable Player target, boolean followingTurn, @Nullable String status) {
        StringBuilder sb = new StringBuilder("**Test bed panel: ")
                .append(game.getName())
                .append("** · ")
                .append(gameLine(game))
                .append('\n');
        if (target == null) {
            sb.append("Acting as: **yourself**");
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
        if (followingTurn) sb.append(" · following the turn");
        appendStatus(sb, status);
        return sb.toString();
    }

    private static String gameLine(Game game) {
        StringBuilder line = new StringBuilder("round ").append(game.getRound());
        if (!game.getPhaseOfGame().isBlank()) line.append(" · ").append(game.getPhaseOfGame());
        Player active = game.getActivePlayer();
        if (active != null) line.append(" · active: ").append(active.getFaction());
        String preset = TestBedApplyService.appliedPreset(game);
        if (!preset.isEmpty()) {
            line.append(" · preset `").append(preset).append('`');
            line.append(TestBedSnapshotService.exists(game.getName()) ? " (reset restores it)" : "");
        }
        if (TestBedService.allowsRealPlayers(game)) line.append(" · real players");
        return line.toString();
    }

    public static List<ActionRow> components(Game game, @Nullable Player target, boolean followingTurn) {
        List<Button> seatButtons = new ArrayList<>();
        for (Player seat : game.getRealPlayers()) {
            String label = seat.getFaction() + " (" + seat.getColor() + ")";
            String id = ACT_AS + seat.getFaction();
            seatButtons.add(seat == target && !followingTurn ? Buttons.green(id, label) : Buttons.gray(id, label));
        }
        seatButtons.add(Buttons.blue(ACT_AS + ACT_AS_ME, "Me"));
        String turnId = ACT_AS + ACT_AS_TURN;
        seatButtons.add(followingTurn ? Buttons.green(turnId, "Follow Turn") : Buttons.gray(turnId, "Follow Turn"));

        List<ActionRow> rows = new ArrayList<>(rowsOf(seatButtons));
        rows.add(ActionRow.of(
                Buttons.gray(TOOL + Tool.active.name(), Tool.active.label),
                Buttons.blue(TOOL + Tool.turn.name(), Tool.turn.label),
                Buttons.blue(TOOL + Tool.buttons.name(), Tool.buttons.label),
                Buttons.gray(TOOL + Tool.refresh.name(), Tool.refresh.label)));
        return rows;
    }

    public static String pageContent(
            Game game, @Nullable Player target, ButtonGroup group, int page, @Nullable String status) {
        StringBuilder sb = new StringBuilder("**Test buttons: ")
                .append(group.name())
                .append("** · page ")
                .append(page + 1)
                .append('/')
                .append(pageCount(group))
                .append(" · acting as **")
                .append(target == null ? "yourself" : target.getFaction())
                .append("** · ")
                .append(gameLine(game));
        if (!group.description().isBlank()) sb.append('\n').append(group.description());
        appendStatus(sb, status);
        return sb.toString();
    }

    public static List<ActionRow> pageComponents(List<ButtonGroup> groups, ButtonGroup group, int page) {
        List<ActionRow> rows = new ArrayList<>();
        if (groups.size() > 1) rows.add(ActionRow.of(groupSelect(groups, group)));
        List<TestBedButton> buttons = group.buttons();
        List<Button> shown = new ArrayList<>();
        int first = page * PAGE_SIZE;
        for (int i = first; i < Math.min(first + PAGE_SIZE, buttons.size()); i++) {
            String id = RUN + group.key() + "_" + i;
            TestBedButton button = buttons.get(i);
            shown.add(button.java() != null ? Buttons.gray(id, button.label()) : Buttons.blue(id, button.label()));
        }
        rows.addAll(rowsOf(shown));
        Button previous = Buttons.gray(PAGE + group.key() + "_" + (page - 1), "◀");
        Button next = Buttons.gray(PAGE + group.key() + "_" + (page + 1), "▶");
        rows.add(ActionRow.of(
                page > 0 ? previous : previous.asDisabled(),
                page < pageCount(group) - 1 ? next : next.asDisabled(),
                Buttons.blue(BACK, "Back")));
        return rows;
    }

    public static String turnContent(Game game, TurnButtons turn, @Nullable String status) {
        StringBuilder sb =
                new StringBuilder("**Turn buttons** · ").append(gameLine(game)).append('\n');
        if (turn.active() == null) {
            sb.append("No active player. Pick a seat and press **Make Active**.");
        } else if (turn.message() == null) {
            sb.append("No buttons for **")
                    .append(turn.active().getFaction())
                    .append("** in their channel's latest messages.");
        } else if (turn.combat() && turn.pressAs() != null) {
            sb.append("Combat buttons in ")
                    .append(turn.message().getChannel().getAsMention())
                    .append("; pressing one counts as **")
                    .append(turn.pressAs().getFaction())
                    .append("**. Switch seats to roll for the other side.");
        } else {
            sb.append("The bot's latest buttons for **")
                    .append(turn.active().getFaction())
                    .append("**; pressing one counts as them.");
            if (turn.buttons().size() > turn.shown().size()) {
                sb.append(" Showing the first ").append(turn.shown().size()).append('.');
            }
        }
        appendStatus(sb, status);
        return sb.toString();
    }

    public static List<ActionRow> turnComponents(Game game, TurnButtons turn) {
        List<Button> shown = new ArrayList<>();
        List<Button> buttons = turn.shown();
        for (int i = 0; i < buttons.size(); i++) {
            shown.add(buttons.get(i).withCustomId(TURN_PRESS + i));
        }
        List<ActionRow> rows = new ArrayList<>(rowsOf(shown));
        List<Button> seats = turn.combat() ? combatSeatButtons(game, turn) : List.of();
        if (!seats.isEmpty()) rows.add(ActionRow.of(seats));
        rows.add(ActionRow.of(Buttons.gray(TOOL + Tool.turn.name(), Tool.refresh.label), Buttons.blue(BACK, "Back")));
        return rows;
    }

    private static List<Button> combatSeatButtons(Game game, TurnButtons turn) {
        String threadName = turn.message().getChannel().getName();
        List<Button> seats = new ArrayList<>();
        for (Player seat : game.getRealPlayers()) {
            String side = game.isFowMode() ? seat.getColor() : seat.getFaction();
            if (!threadName.contains("-" + side + "-") && !threadName.endsWith("-" + side)) continue;
            String id = TURN_ACT_AS + seat.getFaction();
            String label = "Roll as " + seat.getFaction();
            seats.add(seat == turn.pressAs() ? Buttons.green(id, label) : Buttons.gray(id, label));
            if (seats.size() == MAX_COMBAT_SEATS) break;
        }
        return seats;
    }

    private static StringSelectMenu groupSelect(List<ButtonGroup> groups, ButtonGroup current) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(GROUP_SELECT).setPlaceholder("Pick a group");
        for (ButtonGroup group : groups.subList(0, Math.min(groups.size(), MAX_SELECT_OPTIONS))) {
            SelectOption option = SelectOption.of(StringUtils.left(group.name(), MAX_OPTION_TEXT), group.key())
                    .withDefault(group.key().equals(current.key()));
            if (!group.description().isBlank()) {
                option = option.withDescription(StringUtils.abbreviate(group.description(), MAX_OPTION_TEXT));
            }
            menu.addOptions(option);
        }
        return menu.build();
    }

    public static int pageCount(ButtonGroup group) {
        return Math.max(1, (group.buttons().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public static PageRef parseRef(String buttonId, String prefix) {
        String rest = buttonId.substring(prefix.length());
        int separator = rest.lastIndexOf('_');
        return new PageRef(rest.substring(0, separator), Integer.parseInt(rest.substring(separator + 1)));
    }

    private static List<ActionRow> rowsOf(List<Button> buttons) {
        List<ActionRow> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += BUTTONS_PER_ROW) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + BUTTONS_PER_ROW, buttons.size()))));
        }
        return rows;
    }

    private static void appendStatus(StringBuilder sb, @Nullable String status) {
        if (status == null || status.isBlank()) return;
        sb.append("\n> ").append(StringUtils.abbreviate(status, MAX_STATUS).replace("\n", "\n> "));
    }
}
