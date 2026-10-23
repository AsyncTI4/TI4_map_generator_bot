package ti4.service.leader.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.service.leader.ExhaustLeaderService;
import ti4.spring.context.SpringContext;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.spring.service.gameevent.GameSubEvent;

@UtilityClass
public class AgentLifecycle {

    private static final List<String> MESSAGES_TO_DELETE_WHOLE = List.of(
            "choose the user of the agent",
            "please choose the target",
            "please choose the faction to give",
            "choose the target of the agent");

    public static boolean resolveWithModule(
            String buttonId, GenericInteractionCreateEvent event, Game game, Player player) {
        AgentButtonIds.Parsed parsed = AgentButtonIds.parse(buttonId);
        Optional<AgentModule<?>> module = AgentModules.find(parsed.agentId());
        module.ifPresent(found -> resolve(found, parsed, event, game, player));
        return module.isPresent();
    }

    private static <P> void resolve(
            AgentModule<P> module,
            AgentButtonIds.Parsed parsed,
            GenericInteractionCreateEvent event,
            Game game,
            Player player) {
        Leader leader = player.getLeader(parsed.agentId()).orElse(null);
        if (leader == null) {
            return;
        }
        P payload = module.decode(game, player, parsed.payload()).orElse(null);
        String agentName =
                AgentNames.cardName(module.displayName(), AgentNames.isYssarilCopy(leader.getId(), parsed.agentId()));
        if (payload == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Could not work out how to resolve " + agentName + ", so it was not exhausted.");
            return;
        }

        exhaust(game, player, leader, parsed.agentId());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentation() + " has exhausted " + agentName + ".");
        AgentUse<P> use = new AgentUse<>(game, player, leader, parsed.agentId(), module.displayName(), payload, event);
        deliver(module.resolve(use));
        finish(event, game, player, parsed.agentId());
    }

    public static void exhaust(Game game, Player player, Leader leader, String agentId) {
        if (!GameEventDraft.stage(game, new GameSubEvent.LeaderPlayed(player.getFaction(), "AGENT", agentId))) {
            GameEventService.commit(game, GameEventType.CARD_PLAY_AGENT, player, Map.of("cardId", agentId));
        }
        ExhaustLeaderService.exhaustLeader(game, player, leader);
        leader.getLeaderModel().ifPresent(model -> {
            SpringContext.getBean(CombatReplayService.class)
                    .mirrorLeaderPlayed(
                            game,
                            player,
                            model.getAlias(),
                            player.getCorrectChannel().getName());
            recordInActionSummary(game, player, model);
        });
    }

    private static void recordInActionSummary(Game game, Player player, LeaderModel model) {
        String key = "currentActionSummary" + player.getFaction();
        game.setStoredValue(key, game.getStoredValue(key) + " Exhausted the " + model.getAlias() + " leader.");
    }

    public static void finish(GenericInteractionCreateEvent event, Game game, Player player, String agentId) {
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            cleanUpPressedButton(buttonEvent);
        }
        offerTemporalCommandSuite(game, player, agentId);
    }

    private static void deliver(AgentOutcome outcome) {
        for (AgentOutcome.Message message : outcome.messages()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    message.recipient().getCorrectChannel(), message.text(), message.buttons());
        }
    }

    private static void cleanUpPressedButton(ButtonInteractionEvent buttonEvent) {
        String pressedMessage = buttonEvent.getMessage().getContentRaw();
        boolean hasOtherButtons = buttonEvent.getMessage().getComponentTree().findAll(ActionRow.class).stream()
                .map(ActionRow::getComponents)
                .anyMatch(components -> !components.isEmpty());
        if (hasOtherButtons && !isTargetPicker(pressedMessage)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(buttonEvent);
        } else {
            buttonEvent.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static boolean isTargetPicker(String pressedMessage) {
        return pressedMessage.toLowerCase().contains("wanna ")
                || MESSAGES_TO_DELETE_WHOLE.stream().anyMatch(pressedMessage::contains);
    }

    private static void offerTemporalCommandSuite(Game game, Player player, String agentId) {
        for (Player p2 : game.getRealPlayers()) {
            if (!p2.hasTech("tcs") || p2.getExhaustedTechs().contains("tcs")) {
                continue;
            }
            List<Button> buttons = new ArrayList<>();
            String msg;
            if (game.isTwilightsFallMode()) {
                buttons.add(Buttons.green(
                        p2.factionButtonChecker() + "useTCS_" + agentId + "_" + player.getFaction(),
                        "Spend A Command Token to Ready " + agentId));
                msg = p2.getRepresentationNoPing()
                        + " you have the opportunity to spend a command token via _Temporal Command Suite_ to ready "
                        + agentId
                        + ", and potentially resolve a transaction.";
            } else {
                buttons.add(Buttons.green(
                        p2.factionButtonChecker() + "exhaustTCS_" + agentId + "_" + player.getFaction(),
                        "Exhaust Temporal Command Suite to Ready " + agentId));
                msg = p2.getRepresentationNoPing()
                        + " you have the opportunity to exhaust _Temporal Command Suite_ to ready " + agentId
                        + ", and potentially resolve a transaction.";
            }
            buttons.add(Buttons.red(p2.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(p2.getCardsInfoThread(), msg, buttons);
        }
    }
}
