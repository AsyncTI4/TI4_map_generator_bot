package ti4.discord.interactions.buttons.handlers.testbed;

import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IDeferrableCallback;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.testbed.TestBedPanelService;
import ti4.service.testbed.TestBedPanelService.PageRef;
import ti4.service.testbed.TestBedPanelService.Tool;
import ti4.service.testbed.TestBedScriptRunner;
import ti4.service.testbed.TestBedService;
import ti4.service.testbed.TestBedShortcuts;
import ti4.service.testbed.TestBedShortcuts.ButtonGroup;
import ti4.service.testbed.TestBedShortcuts.TestBedButton;
import ti4.service.testbed.TestBedTurnButtons;
import ti4.service.testbed.TestBedTurnButtons.TurnButtons;
import ti4.service.turn.StartTurnService;

@UtilityClass
class TestBedPanelButtonHandler {

    @ButtonHandler(TestBedPanelService.ACT_AS)
    public static void actAs(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game, event.getButton().getLabel())) return;
        String target = buttonID.substring(TestBedPanelService.ACT_AS.length());
        String userId = event.getUser().getId();
        if (TestBedPanelService.ACT_AS_TURN.equals(target)) {
            TestBedService.followTurn(game, userId);
        } else {
            Player seat =
                    TestBedPanelService.ACT_AS_ME.equals(target) ? null : game.getPlayerFromColorOrFaction(target);
            TestBedService.setActingAs(game, userId, seat);
        }
        Player actingAs = actingPlayer(event, game);
        showMain(event, game, "Now acting as " + (actingAs == null ? "yourself" : actingAs.getFaction()) + ".");
    }

    @ButtonHandler(TestBedPanelService.TOOL)
    public static void tool(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game, event.getButton().getLabel())) return;
        Tool tool = Tool.valueOf(buttonID.substring(TestBedPanelService.TOOL.length()));
        switch (tool) {
            case refresh -> showMain(event, game, null);
            case turn -> showTurn(event, game, null);
            case buttons -> {
                List<ButtonGroup> groups = TestBedShortcuts.groups(game);
                showPage(event, game, groups.getFirst().key(), 0, null);
            }
            case active -> {
                Player target = actingPlayer(event, game);
                if (target == null || !target.isRealPlayer()) {
                    showMain(event, game, "Pick a seat to act as first.");
                    return;
                }
                game.updateActivePlayer(target);
                StartTurnService.turnStart(event, game, target);
                showMain(event, game, target.getFaction() + " is now the active player.");
            }
        }
    }

    @ButtonHandler(TestBedPanelService.TURN_PRESS)
    public static void turnPress(ButtonInteractionEvent event, Game game, String buttonID) {
        String label = event.getButton().getLabel();
        if (!isAllowed(event, game, label)) return;
        int index = Integer.parseInt(buttonID.substring(TestBedPanelService.TURN_PRESS.length()));
        showTurn(event, game, "Pressing `" + label + "`…");
        String gameName = game.getName();
        TestBedTurnButtons.press(
                game, event.getMember(), index, label, status -> showTurnLater(event, gameName, status));
    }

    @ButtonHandler(TestBedPanelService.PAGE)
    public static void page(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game, null)) return;
        PageRef ref = TestBedPanelService.parseRef(buttonID, TestBedPanelService.PAGE);
        showPage(event, game, ref.groupKey(), ref.number(), null);
    }

    @ButtonHandler(TestBedPanelService.BACK)
    public static void back(ButtonInteractionEvent event, Game game) {
        if (!isAllowed(event, game, null)) return;
        showMain(event, game, null);
    }

    @SelectionHandler(TestBedPanelService.GROUP_SELECT)
    public static void selectGroup(StringSelectInteractionEvent event, Game game) {
        if (!isAllowed(event, game, null) || event.getValues().isEmpty()) return;
        showPage(event, game, event.getValues().getFirst(), 0, null);
    }

    @ButtonHandler(TestBedPanelService.RUN)
    public static void run(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game, event.getButton().getLabel())) return;
        PageRef ref = TestBedPanelService.parseRef(buttonID, TestBedPanelService.RUN);
        ButtonGroup group = TestBedShortcuts.group(game, ref.groupKey());
        if (group == null || ref.number() >= group.buttons().size()) {
            showMain(event, game, "That test button no longer exists.");
            return;
        }
        int page = ref.number() / TestBedPanelService.PAGE_SIZE;
        TestBedButton button = group.buttons().get(ref.number());
        if (button.java() != null) {
            String status = button.java().action().run(game, actingPlayer(event, game), event);
            showPage(event, game, group.key(), page, button.label() + ": " + status);
            return;
        }
        showPage(event, game, group.key(), page, button.label() + ": running…");
        String gameName = game.getName();
        TestBedScriptRunner.startShortcut(
                game,
                button.label(),
                button.steps(),
                event,
                status -> showPageLater(event, gameName, group.key(), page, status));
    }

    private static boolean isAllowed(GenericInteractionCreateEvent event, Game game, @Nullable String loggedLabel) {
        if (TestBedService.isTestBed(game) && TestBedService.isDeveloper(event.getMember())) {
            if (loggedLabel != null) {
                Player target = actingPlayer(event, game);
                TestBedService.logPanelUse(
                        game,
                        event.getUser().getName(),
                        target == null ? "themselves" : target.getFaction(),
                        loggedLabel);
            }
            return true;
        }
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "The test bed panel only works for developers in a test bed game.");
        return false;
    }

    @Nullable
    private static Player actingPlayer(GenericInteractionCreateEvent event, Game game) {
        return TestBedService.resolveActingPlayerForComponent(
                game, event, game.getPlayer(event.getUser().getId()));
    }

    private static void showMain(GenericInteractionCreateEvent event, Game game, @Nullable String status) {
        Player target = actingPlayer(event, game);
        boolean following = TestBedService.isFollowingTurn(game, event.getUser().getId());
        ((IDeferrableCallback) event)
                .getHook()
                .editOriginal(TestBedPanelService.content(game, target, following, status))
                .setComponents(TestBedPanelService.components(game, target, following))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void showPage(
            GenericInteractionCreateEvent event, Game game, String groupKey, int page, @Nullable String status) {
        List<ButtonGroup> groups = TestBedShortcuts.groups(game);
        ButtonGroup group = groups.stream()
                .filter(candidate -> candidate.key().equals(groupKey))
                .findFirst()
                .orElse(groups.getFirst());
        int shownPage = Math.clamp(page, 0, TestBedPanelService.pageCount(group) - 1);
        ((IDeferrableCallback) event)
                .getHook()
                .editOriginal(
                        TestBedPanelService.pageContent(game, actingPlayer(event, game), group, shownPage, status))
                .setComponents(TestBedPanelService.pageComponents(groups, group, shownPage))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void showTurn(GenericInteractionCreateEvent event, Game game, @Nullable String status) {
        TurnButtons turn = TestBedTurnButtons.find(game);
        ((IDeferrableCallback) event)
                .getHook()
                .editOriginal(TestBedPanelService.turnContent(game, turn, status))
                .setComponents(TestBedPanelService.turnComponents(turn))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void showTurnLater(GenericInteractionCreateEvent event, String gameName, String status) {
        ManagedGame managed = GameManager.getManagedGame(gameName);
        if (managed == null) return;
        showTurn(event, managed.getGame(), status);
    }

    private static void showPageLater(
            GenericInteractionCreateEvent event, String gameName, String groupKey, int page, String status) {
        ManagedGame managed = GameManager.getManagedGame(gameName);
        if (managed == null) return;
        showPage(event, managed.getGame(), groupKey, page, status);
    }
}
