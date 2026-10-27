package ti4.service.leader.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.ChannelType;
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
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.spring.context.SpringContext;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameEventService;
import ti4.spring.service.gameevent.GameEventType;
import ti4.spring.service.gameevent.GameSubEvent;

@UtilityClass
public class AgentLifecycle {

    private static final String GENOME = "Genome";
    private static final List<String> YSSARIL_AGENTS = List.of("yssarilagent", "yssarilagent_br");
    private static final List<String> MESSAGES_TO_DELETE_WHOLE = List.of(
            "choose the user of the agent",
            "please choose the target",
            "please choose the faction to give",
            "choose the target of the agent");

    public static boolean resolve(String buttonId, GenericInteractionCreateEvent event, Game game, Player player) {
        AgentButtonIds.Parsed parsed = AgentButtonIds.parse(buttonId);
        Optional<AgentModule<?>> module = AgentModules.find(parsed.agentId());
        module.ifPresent(found -> run(found, parsed, event, game, player, true));
        return module.isPresent();
    }

    public static boolean use(
            Game game, Player player, String agentId, String payload, GenericInteractionCreateEvent event) {
        AgentModule<?> module = AgentModules.find(agentId)
                .orElseThrow(() -> new IllegalArgumentException("No agent module for " + agentId));
        return run(module, new AgentButtonIds.Parsed(agentId, payload), event, game, player, false);
    }

    public static boolean refuseIfExhausted(GenericInteractionCreateEvent event, Player player, Leader leader) {
        if (!leader.isExhausted()) {
            return false;
        }
        String message = leader.getLeaderModel().map(LeaderModel::getName).orElse(leader.getId())
                + " is already exhausted, so it cannot be used again until it is readied.";
        if (event == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " " + message);
        } else {
            MessageHelper.sendEphemeralMessageToEventChannel(event, message);
        }
        return true;
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
        finish(event, game, player, agentId, false);
    }

    private static void finish(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            String agentId,
            boolean deleteWholePressedMessage) {
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            cleanUpPressedButton(buttonEvent, deleteWholePressedMessage);
        }
        offerTemporalCommandSuite(game, player, agentId);
    }

    public static String agentName(String cardName, boolean viaYssaril) {
        if (cardName.endsWith(GENOME)) {
            return viaYssaril ? "the Clever Clever _" + cardName + "_" : "the _" + cardName + "_";
        }
        if (!viaYssaril) {
            return cardName + " agent";
        }
        return "Clever Clever " + cardName + "/Yssaril agent";
    }

    public static String agentNameForHolder(Game game, Player holder, String leaderId) {
        return agentName(game, leaderId, holdsOnlyAYssarilCopy(holder, leaderId));
    }

    public static String agentNameShownTo(
            Game game, Player reader, Player holder, String leaderId, boolean viaYssaril) {
        if (game.isFowMode() && reader != holder) {
            return AgentUse.FOG_SAFE_AGENT_NAME;
        }
        return agentName(game, leaderId, viaYssaril);
    }

    static String agentName(Game game, String leaderId, boolean viaYssaril) {
        AgentModule<?> module = AgentModules.find(leaderId)
                .orElseThrow(() -> new IllegalArgumentException("No agent module for " + leaderId));
        return agentName(cardName(game, module, leaderId), viaYssaril);
    }

    static String cardName(Game game, AgentModule<?> module, String leaderId) {
        return AgentModule.twilightsFallName(game, leaderId).orElse(module.displayName(leaderId));
    }

    static boolean isYssarilCopy(String exhaustedLeaderId, String agentId) {
        return isYssarilAgent(exhaustedLeaderId) && !isYssarilAgent(agentId);
    }

    static boolean holdsOnlyAYssarilCopy(Player player, String agentId) {
        return !player.hasLeader(agentId) && YSSARIL_AGENTS.stream().anyMatch(player::hasLeader);
    }

    private static boolean isYssarilAgent(String leaderId) {
        return YSSARIL_AGENTS.stream().anyMatch(leaderId::equalsIgnoreCase);
    }

    private static <P> boolean run(
            AgentModule<P> module,
            AgentButtonIds.Parsed parsed,
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            boolean pressedAgentButton) {
        String agentId = parsed.agentId();
        Leader leader = player.getLeader(agentId).orElse(null);
        if (leader == null || refuseIfExhausted(pressedAgentButton ? event : null, player, leader)) {
            return false;
        }
        boolean viaYssaril = isYssarilCopy(leader.getId(), agentId);
        String agentName = agentName(cardName(game, module, agentId), viaYssaril);
        MessageChannel replyChannel = replyChannel(player, pressedAgentButton ? event : null);
        Optional<P> payload = module.decode(game, player, parsed.payload());
        if (payload.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    replyChannel, "Could not work out how to resolve " + agentName + ", so it was not exhausted.");
            return false;
        }
        Optional<String> unusable = module.unusableReason(game, player, payload.get());
        if (unusable.isPresent()) {
            MessageHelper.sendMessageToChannel(
                    replyChannel, unusable.get() + ", so " + agentName + " was not exhausted.");
            return false;
        }

        exhaust(game, player, leader, agentId, parsed.hasPayload() ? parsed.payload() : null);
        AgentUse<P> use = new AgentUse<>(game, player, payload.get(), agentName, viaYssaril, event);
        GenericInteractionCreateEvent pressedButton = pressedAgentButton ? event : null;
        deliver(
                AgentOutcome.of(Message.addressedTo(player, module.exhaustAnnouncementAfterName(use))
                        .forEveryone()),
                pressedButton,
                player);
        AgentOutcome outcome = module.resolve(use);
        deliver(outcome, pressedButton, player);
        if (pressedAgentButton) {
            finish(event, game, player, agentId, outcome.deletesPressedMessage());
        } else {
            offerTemporalCommandSuite(game, player, agentId);
        }
        return true;
    }

    private static MessageChannel replyChannel(Player player, GenericInteractionCreateEvent pressedButton) {
        return pressedButton != null ? pressedButton.getMessageChannel() : player.getCorrectChannel();
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

    public static void deliver(AgentOutcome outcome, GenericInteractionCreateEvent pressedButton, Player presser) {
        Set<Player> sentButtonsElsewhere = new LinkedHashSet<>();
        for (Message message : outcome.messages()) {
            MessageChannel channel = deliveryChannel(message, pressedButton, presser);
            String text = message.namesRecipient()
                    ? addresseeName(message.recipient(), channel, pressedButton, presser) + message.text()
                    : message.text();
            send(channel, text, message);
            if (sentAnotherPlayerButtonsElsewhere(message, channel, pressedButton, presser)) {
                sentButtonsElsewhere.add(message.recipient());
            }
        }
        for (Player recipient : sentButtonsElsewhere) {
            MessageHelper.sendMessageToChannel(
                    replyChannel(presser, pressedButton), sentButtonsConfirmation(recipient));
        }
        if (outcome.pressedMessageEdit() != null && pressedButton instanceof ButtonInteractionEvent buttonEvent) {
            buttonEvent
                    .getMessage()
                    .editMessage(outcome.pressedMessageEdit())
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static void send(MessageChannel channel, String text, Message message) {
        List<Button> buttons = new ArrayList<>(message.buttons());
        if (message.pagedButtons()) {
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, text, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(channel, text, buttons);
        }
    }

    static boolean sentAnotherPlayerButtonsElsewhere(
            Message message, MessageChannel deliveredTo, GenericInteractionCreateEvent pressedButton, Player presser) {
        return pressedButton != null
                && message.recipient() != presser
                && !message.buttons().isEmpty()
                && !sameChannel(deliveredTo, pressedButton.getMessageChannel());
    }

    static String sentButtonsConfirmation(Player recipient) {
        String who = recipient.getGame().isFowMode() ? "the chosen player" : recipient.getRepresentationNoPing();
        return "Sent the buttons to " + who + ".";
    }

    static String addresseeName(
            Player recipient, MessageChannel channel, GenericInteractionCreateEvent pressedButton, Player presser) {
        boolean presserIsReadingThisChannel = recipient == presser
                && pressedButton != null
                && sameChannel(channel, pressedButton.getMessageChannel());
        return presserIsReadingThisChannel
                ? recipient.getRepresentationNoPing()
                : recipient.getRepresentationUnfogged();
    }

    static MessageChannel deliveryChannel(
            Message message, GenericInteractionCreateEvent pressedButton, Player presser) {
        Player recipient = message.recipient();
        if (message.route() == Message.Route.CARDS_INFO_THREAD) {
            return recipient.getCardsInfoThread();
        }
        if (message.route() == Message.Route.EVERYONE && pressedInPrivateThread(pressedButton)) {
            return recipient.getCorrectChannel();
        }
        if (recipient != presser && (presser.getGame().isFowMode() || pressedInPrivateThread(pressedButton))) {
            return recipient.getCorrectChannel();
        }
        return replyChannel(presser, pressedButton);
    }

    private static boolean pressedInPrivateThread(GenericInteractionCreateEvent pressedButton) {
        return pressedButton != null && pressedButton.getMessageChannel().getType() == ChannelType.GUILD_PRIVATE_THREAD;
    }

    private static boolean sameChannel(MessageChannel first, MessageChannel second) {
        return first != null && second != null && first.getId().equals(second.getId());
    }

    private static void cleanUpPressedButton(ButtonInteractionEvent buttonEvent, boolean deleteWholeMessage) {
        if (deletesWholeMessage(buttonEvent.getMessage().getContentRaw(), deleteWholeMessage)) {
            buttonEvent.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(buttonEvent);
        }
    }

    static boolean deletesWholeMessage(String pressedMessage, boolean outcomeAsksForIt) {
        return outcomeAsksForIt || isTargetPicker(pressedMessage);
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
