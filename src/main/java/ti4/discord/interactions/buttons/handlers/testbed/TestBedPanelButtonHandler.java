package ti4.discord.interactions.buttons.handlers.testbed;

import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.TestBedScript.Shortcut;
import ti4.service.game.StartPhaseService;
import ti4.service.info.CardsInfoService;
import ti4.service.testbed.TestBedPanelService;
import ti4.service.testbed.TestBedPanelService.Tool;
import ti4.service.testbed.TestBedScriptRunner;
import ti4.service.testbed.TestBedService;
import ti4.service.testbed.TestBedShortcuts;
import ti4.service.testbed.TestBedShortcuts.JavaShortcut;
import ti4.service.turn.StartTurnService;

@UtilityClass
class TestBedPanelButtonHandler {

    @ButtonHandler(TestBedPanelService.ACT_AS)
    public static void actAs(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game)) return;
        String target = buttonID.substring(TestBedPanelService.ACT_AS.length());
        Player seat = TestBedPanelService.ACT_AS_ME.equals(target) ? null : game.getPlayerFromColorOrFaction(target);
        TestBedService.setActingAs(game, event.getUser().getId(), seat);
        Player actingAs = actingPlayer(event, game);
        refresh(
                event,
                game,
                actingAs,
                "Now acting as " + (actingAs == null ? "yourself" : actingAs.getFaction()) + ".");
    }

    @ButtonHandler(TestBedPanelService.TOOL)
    public static void tool(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game)) return;
        Tool tool = Tool.valueOf(buttonID.substring(TestBedPanelService.TOOL.length()));
        Player target = actingPlayer(event, game);
        if (tool == Tool.refresh) {
            refresh(event, game, target, null);
            return;
        }
        if (tool == Tool.shortcuts) {
            event.getHook()
                    .sendMessage("**Test bed shortcuts.** Blue ones come from the preset, gray ones are built in.")
                    .setComponents(TestBedPanelService.shortcutComponents(game))
                    .setEphemeral(true)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }
        if (target == null || !target.isRealPlayer()) {
            refresh(event, game, target, "Pick a seat to act as first.");
            return;
        }
        String status =
                switch (tool) {
                    case active -> {
                        game.updateActivePlayer(target);
                        StartTurnService.turnStart(event, game, target);
                        yield target.getFaction() + " is now the active player.";
                    }
                    case cards -> {
                        CardsInfoService.sendCardsInfo(game, target, event);
                        yield "Sent cards info to " + target.getFaction() + ".";
                    }
                    default -> TestBedPanelService.applyTool(tool, target);
                };
        refresh(event, game, target, status);
    }

    @ButtonHandler(TestBedPanelService.JSON_SHORTCUT)
    public static void jsonShortcut(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game)) return;
        int index = Integer.parseInt(buttonID.substring(TestBedPanelService.JSON_SHORTCUT.length()));
        List<Shortcut> shortcuts = TestBedShortcuts.load(game);
        if (index >= shortcuts.size()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That shortcut no longer exists.");
            return;
        }
        Shortcut shortcut = shortcuts.get(index);
        TestBedScriptRunner.startShortcut(game, shortcut.getLabel(), shortcut.getSteps(), event);
    }

    @ButtonHandler(TestBedPanelService.JAVA_SHORTCUT)
    public static void javaShortcut(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game)) return;
        JavaShortcut shortcut =
                TestBedShortcuts.javaShortcut(buttonID.substring(TestBedPanelService.JAVA_SHORTCUT.length()));
        if (shortcut == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Unknown shortcut.");
            return;
        }
        String status = shortcut.action().run(game, actingPlayer(event, game), event);
        MessageHelper.sendEphemeralMessageToEventChannel(event, "**" + shortcut.label() + "**: " + status);
    }

    @ButtonHandler(TestBedPanelService.PHASE)
    public static void phase(ButtonInteractionEvent event, Game game, String buttonID) {
        if (!isAllowed(event, game)) return;
        String phase = buttonID.substring(TestBedPanelService.PHASE.length());
        StartPhaseService.startPhase(event, game, phase);
        refresh(event, game, actingPlayer(event, game), "Started `" + phase + "`.");
    }

    private static boolean isAllowed(ButtonInteractionEvent event, Game game) {
        if (TestBedService.isTestBed(game) && TestBedService.isDeveloper(event.getMember())) return true;
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "The test bed panel only works for developers in a test bed game.");
        return false;
    }

    @Nullable
    private static Player actingPlayer(ButtonInteractionEvent event, Game game) {
        return TestBedService.resolveActingPlayer(
                game, event, game.getPlayer(event.getUser().getId()));
    }

    private static void refresh(
            ButtonInteractionEvent event, Game game, @Nullable Player target, @Nullable String status) {
        event.getHook()
                .editOriginal(TestBedPanelService.content(game, target, status))
                .setComponents(TestBedPanelService.components(game, target))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
