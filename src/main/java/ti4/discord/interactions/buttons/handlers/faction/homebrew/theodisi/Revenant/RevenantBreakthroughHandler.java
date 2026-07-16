package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.UnusedAgentHelper;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class RevenantBreakthroughHandler {
    private static final String REVENANT_RISING = "revenantbt";
    private static final String ATTACHED_AGENTS = "revenantRisingAgents_";
    private static final String PURGE_ROUND = "revenantRisingPurgeRound_";
    private static final String PURGE_ENTRY_MESSAGE = "revenantRisingPurgeEntryMessage_";
    private static final String OPEN_PURGE_MENU = "revenantRisingPurgeAgent";
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
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " drew and attached _" + agentName + "_ to _Revenant Rising_.",
                agent.getRepresentationEmbed());
        if (agent != null && player.getCardsInfoThread() != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCardsInfoThread(), "__Revenant Rising Attached Agent__", agent.getRepresentationEmbed());
        }
    }

    public static Button getPurgeAgentButton(Player player) {
        return Buttons.red(player.factionButtonChecker() + OPEN_PURGE_MENU, "Purge Revenant Rising Agent");
    }

    public static boolean canPurgeAgent(Game game, Player player) {
        return game != null
                && player != null
                && player.hasUnlockedBreakthrough(REVENANT_RISING)
                && !hasUsedPurgeThisRound(game, player)
                && !getAttachedAgents(game, player).isEmpty();
    }

    public static void exhaustRevenantRisingForAttachedAgent(Game game, Player player, Leader exhaustedLeader) {
        if (!isReadyRevenantRisingAttachedAgent(game, player, exhaustedLeader)) return;

        BreakthroughCommandHelper.exhaustBreakthrough(player, REVENANT_RISING);
    }

    public static boolean isReadyRevenantRisingAttachedAgent(Game game, Player player, Leader leader) {
        return game != null
                && player != null
                && leader != null
                && player.hasUnlockedBreakthrough(REVENANT_RISING)
                && player.hasReadyBreakthrough(REVENANT_RISING)
                && getAttachedAgents(game, player).contains(leader.getId());
    }

    @ButtonHandler(OPEN_PURGE_MENU)
    public static void offerPurgeAgentButtons(ButtonInteractionEvent event, Game game, Player player) {
        if (!canPurgeAgent(game, player)) {
            return;
        }

        if (player.getCardsInfoThread() != null
                && player.getCardsInfoThread()
                        .getId()
                        .equals(event.getMessageChannel().getId())) {
            game.setStoredValue(PURGE_ENTRY_MESSAGE + player.getFaction(), event.getMessageId());
        }

        List<Button> buttons = new ArrayList<>();
        for (String attachedAgentId : getAttachedAgents(game, player)) {
            LeaderModel attachedAgent = Mapper.getLeader(attachedAgentId);
            String agentName = attachedAgent == null ? attachedAgentId : attachedAgent.getName();
            buttons.add(
                    Buttons.red(player.factionButtonChecker() + PURGE_AGENT + attachedAgentId, "Purge " + agentName));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Cancel"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose an agent attached to _Revenant Rising_ to purge.",
                buttons);
    }

    @ButtonHandler(PURGE_AGENT)
    public static void purgeAttachedAgent(ButtonInteractionEvent event, Game game, Player player, String buttonId) {
        if (game == null || player == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String agentId = buttonId.substring(PURGE_AGENT.length());
        if (!canPurgeAgent(game, player)) {
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
                + "_ instead of exhausting it for its effect.";
        removePurgeAgentCardsInfoButton(game, player);
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), result);
        ButtonHelper.deleteMessage(event);
    }

    private static void removePurgeAgentCardsInfoButton(Game game, Player player) {
        String messageId = game.getStoredValue(PURGE_ENTRY_MESSAGE + player.getFaction());
        game.removeStoredValue(PURGE_ENTRY_MESSAGE + player.getFaction());
        if (messageId.isBlank() || player.getCardsInfoThread() == null) {
            return;
        }

        String purgeButtonId = player.factionButtonChecker() + OPEN_PURGE_MENU;
        player.getCardsInfoThread()
                .retrieveMessageById(messageId)
                .queue(
                        message -> {
                            MessageComponentTree updatedTree = message.getComponentTree()
                                    .replace(ComponentReplacer.of(
                                            ActionRow.class,
                                            row -> row.getComponents().stream()
                                                    .anyMatch(component -> component instanceof Button button
                                                            && purgeButtonId.equals(button.getCustomId())),
                                            row -> {
                                                List<ActionRowChildComponentUnion> kept = row.getComponents().stream()
                                                        .filter(component -> !(component instanceof Button button
                                                                && purgeButtonId.equals(button.getCustomId())))
                                                        .toList();
                                                return kept.isEmpty() ? null : ActionRow.of(kept);
                                            }));
                            message.editMessageComponents(updatedTree)
                                    .queue(Consumers.nop(), BotLogger::catchRestError);
                        },
                        BotLogger::catchRestError);
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
