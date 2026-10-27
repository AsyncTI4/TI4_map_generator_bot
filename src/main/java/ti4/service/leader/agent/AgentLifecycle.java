package ti4.service.leader.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
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

    private static final String YSSARIL_AGENT = "yssarilagent";
    private static final List<String> MESSAGES_TO_DELETE_WHOLE = List.of(
            "choose the user of the agent",
            "please choose the target",
            "please choose the faction to give",
            "choose the target of the agent");

    public static boolean resolve(String buttonId, GenericInteractionCreateEvent event, Game game, Player player) {
        AgentButtonIds.Parsed parsed = AgentButtonIds.parse(buttonId);
        Optional<AgentModule<?>> module = AgentModules.find(parsed.agentId());
        module.ifPresent(found -> use(found, parsed, event, game, player));
        return module.isPresent();
    }

    public static void exhaust(Game game, Player player, Leader leader, String agentId, String target) {
        recordGameEvent(game, player, agentId, target);
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

    public static void finish(GenericInteractionCreateEvent event, Game game, Player player, String agentId) {
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            cleanUpPressedButton(buttonEvent);
        }
        offerTemporalCommandSuite(game, player, agentId);
    }

    static String agentName(String displayName, String exhaustedLeaderId, String agentId) {
        if (!isYssarilCopy(exhaustedLeaderId, agentId)) {
            return displayName + " agent";
        }
        return "Clever Clever " + displayName + "/Yssaril agent";
    }

    private static boolean isYssarilCopy(String exhaustedLeaderId, String agentId) {
        return YSSARIL_AGENT.equalsIgnoreCase(exhaustedLeaderId) && !YSSARIL_AGENT.equalsIgnoreCase(agentId);
    }

    private static <P> void use(
            AgentModule<P> module,
            AgentButtonIds.Parsed parsed,
            GenericInteractionCreateEvent event,
            Game game,
            Player player) {
        String agentId = parsed.agentId();
        Leader leader = player.getLeader(agentId).orElse(null);
        if (leader == null) {
            return;
        }
        String agentName = agentName(module.displayName(), leader.getId(), agentId);
        Optional<P> payload = module.decode(game, player, parsed.payload());
        if (payload.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Could not work out how to resolve " + agentName + ", so it was not exhausted.");
            return;
        }

        exhaust(game, player, leader, agentId, parsed.hasPayload() ? parsed.payload() : null);
        AgentUse<P> use = new AgentUse<>(game, player, payload.get(), agentName, event);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), module.exhaustAnnouncement(use));
        deliver(module.resolve(use), use);
        finish(event, game, player, agentId);
    }

    private static void recordGameEvent(Game game, Player player, String agentId, String target) {
        if (GameEventDraft.stage(game, new GameSubEvent.LeaderPlayed(player.getFaction(), "AGENT", agentId, target))) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("cardId", agentId);
        payload.put("target", target);
        GameEventService.commit(game, GameEventType.CARD_PLAY_AGENT, player, payload);
    }

    private static void recordInActionSummary(Game game, Player player, LeaderModel model) {
        String key = "currentActionSummary" + player.getFaction();
        game.setStoredValue(key, game.getStoredValue(key) + " Exhausted the " + model.getAlias() + " leader.");
    }

    private static void deliver(AgentOutcome outcome, AgentUse<?> use) {
        for (AgentOutcome.Message message : outcome.messages()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    channelFor(message, use), message.text(), new ArrayList<>(message.buttons()));
        }
        if (outcome.pressedMessageEdit() != null && use.event() instanceof ButtonInteractionEvent buttonEvent) {
            buttonEvent
                    .getMessage()
                    .editMessage(outcome.pressedMessageEdit())
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static MessageChannel channelFor(AgentOutcome.Message message, AgentUse<?> use) {
        if (!message.goesToPressedChannel()) {
            return message.recipient().getCorrectChannel();
        }
        return use.event() != null
                ? use.event().getMessageChannel()
                : use.user().getCorrectChannel();
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
