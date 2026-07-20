package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.UnusedAgentHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class RevenantBreakthroughHandler {
    private static final String REVENANT_RISING = "revenantbt";
    private static final String ATTACHED_AGENTS = "revenantRisingAgents_";
    private static final String PURGE_ROUND = "revenantRisingPurgeRound_";
    private static final String OFFER_PURGE = "revenantRisingOfferPurge_";
    private static final String DECLINE_PURGE = "revenantRisingDeclinePurge";
    private static final String PURGE_AGENT = "revenantRisingPurge_";

    public static void gainAttachedAgent(Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough(REVENANT_RISING)) {
            return;
        }

        String agentId = UnusedAgentHelper.getUnusedAgent(game, Set.of(ComponentSource.theodisi));
        if (agentId == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no unused agents available for _Revenant Rising_.");
            return;
        }

        player.addLeader(agentId);
        game.addFakeAgent(agentId);
        List<String> attachedAgents = getAttachedAgents(game, player);
        attachedAgents.add(agentId);
        saveAttachedAgents(game, player, attachedAgents);

        LeaderModel agent = Mapper.getLeader(agentId);
        String agentName = agent == null ? agentId : agent.getName();
        String attachmentMessage =
                player.getRepresentation() + " drew and attached _" + agentName + "_ to _Revenant Rising_.";
        if (agent == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), attachmentMessage);
        } else {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(), attachmentMessage, agent.getRepresentationEmbed());
        }
        if (agent != null && player.getCardsInfoThread() != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCardsInfoThread(), "__Revenant Rising Attached Agent__", agent.getRepresentationEmbed());
        }
    }

    public static void exhaustRevenantRisingForAttachedAgent(Game game, Player player, Leader exhaustedLeader) {
        if (!isReadyRevenantRisingAttachedAgent(game, player, exhaustedLeader)) return;

        BreakthroughCommandHelper.exhaustBreakthrough(player, REVENANT_RISING);
        if (player.getCardsInfoThread() == null
                || hasUsedPurgeThisRound(game, player)
                || getPurgeableAttachedAgents(game, player, exhaustedLeader.getId())
                        .isEmpty()) {
            return;
        }

        List<Button> buttons = List.of(
                Buttons.red(
                        player.factionButtonChecker() + OFFER_PURGE + exhaustedLeader.getId(),
                        "Purge an Attached Agent"),
                Buttons.gray(player.factionButtonChecker() + DECLINE_PURGE, "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", instead of exhausting _Revenant Rising_, you may purge another attached agent."
                        + " This will ready _Revenant Rising_.",
                buttons);
    }

    public static boolean isReadyRevenantRisingAttachedAgent(Game game, Player player, Leader leader) {
        return game != null
                && player != null
                && leader != null
                && player.hasUnlockedBreakthrough(REVENANT_RISING)
                && player.hasReadyBreakthrough(REVENANT_RISING)
                && getAttachedAgents(game, player).contains(leader.getId());
    }

    @ButtonHandler(OFFER_PURGE)
    public static void offerPurgeAgentButtons(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        String exhaustedAgentId = buttonId.substring(OFFER_PURGE.length());
        if (!canPurgeAttachedAgent(game, player, exhaustedAgentId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String attachedAgentId : getPurgeableAttachedAgents(game, player, exhaustedAgentId)) {
            LeaderModel attachedAgent = Mapper.getLeader(attachedAgentId);
            String agentName = attachedAgent == null ? attachedAgentId : attachedAgent.getName();
            buttons.add(Buttons.red(
                    player.factionButtonChecker() + PURGE_AGENT + exhaustedAgentId + "|" + attachedAgentId,
                    "Purge " + agentName));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose an agent attached to _Revenant Rising_ to purge.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(DECLINE_PURGE)
    public static void declinePurge(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PURGE_AGENT)
    public static void purgeAttachedAgent(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] agentIds = buttonId.substring(PURGE_AGENT.length()).split("\\|", 2);
        if (agentIds.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String exhaustedAgentId = agentIds[0];
        String agentId = agentIds[1];
        if (!canPurgeAttachedAgent(game, player, exhaustedAgentId)
                || !getPurgeableAttachedAgents(game, player, exhaustedAgentId).contains(agentId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Leader purgedLeader = player.getLeader(agentId).orElse(null);
        List<String> attachedAgents = getAttachedAgents(game, player);
        if (!player.hasUnlockedBreakthrough(REVENANT_RISING)
                || purgedLeader == null
                || !attachedAgents.contains(agentId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removeLeader(agentId);
        attachedAgents.remove(agentId);
        saveAttachedAgents(game, player, attachedAgents);
        game.setStoredValue(PURGE_ROUND + player.getFaction(), String.valueOf(game.getRound()));

        LeaderModel purgedAgent = Mapper.getLeader(agentId);
        String purgedName = purgedAgent == null ? agentId : purgedAgent.getName();
        String result = player.getRepresentation() + " chose to purge _" + purgedName
                + "_ instead of exhausting _Revenant Rising_.";
        BreakthroughCommandHelper.readyBreakthrough(player, REVENANT_RISING);
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), result);
        ButtonHelper.deleteMessage(event);
    }

    private static boolean canPurgeAttachedAgent(Game game, Player player, String exhaustedAgentId) {
        return game != null
                && player != null
                && player.hasUnlockedBreakthrough(REVENANT_RISING)
                && !player.hasReadyBreakthrough(REVENANT_RISING)
                && !hasUsedPurgeThisRound(game, player)
                && getAttachedAgents(game, player).contains(exhaustedAgentId)
                && player.getLeader(exhaustedAgentId).map(Leader::isExhausted).orElse(false)
                && !getPurgeableAttachedAgents(game, player, exhaustedAgentId).isEmpty();
    }

    private static List<String> getPurgeableAttachedAgents(Game game, Player player, String exhaustedAgentId) {
        return getAttachedAgents(game, player).stream()
                .filter(agentId -> !agentId.equals(exhaustedAgentId))
                .filter(agentId -> player.getLeader(agentId).isPresent())
                .toList();
    }

    private static boolean hasUsedPurgeThisRound(Game game, Player player) {
        return String.valueOf(game.getRound()).equals(game.getStoredValue(PURGE_ROUND + player.getFaction()));
    }

    private static List<String> getAttachedAgents(Game game, Player player) {
        String storedAgents = game.getStoredValue(ATTACHED_AGENTS + player.getFaction());
        if (storedAgents.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(storedAgents.split("\\|")));
    }

    private static void saveAttachedAgents(Game game, Player player, List<String> attachedAgents) {
        game.setStoredValue(ATTACHED_AGENTS + player.getFaction(), String.join("|", attachedAgents));
    }
}
