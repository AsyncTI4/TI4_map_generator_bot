package ti4.buttons.handlers.faction.zephyrion;

import static org.apache.commons.lang3.StringUtils.capitalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.UnusedAgentHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
class ZephyrionBreakthroughButtonHandler {

    @ButtonHandler("zephyrionbtRes_")
    public static void zephyrionbtRes(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephyrionbtRes_", "");
        String opponentFaction = buttonID;
        String agentID = UnusedAgentHelper.getUnusedAgent(game, Set.of(ComponentSource.balacasi));

        List<Button> buttons = new ArrayList<>();
        if (agentID != null) {
            LeaderModel agentModel = Mapper.getLeader(agentID);
            String agentName = agentModel != null ? agentModel.getName() : agentID;
            if (agentModel != null) {
                MessageHelper.sendMessageToChannelWithEmbed(
                        player.getCardsInfoThread(),
                        player.getRepresentation() + " drew this agent via _Subdue Chancellor_:",
                        agentModel.getRepresentationEmbed());
            }
            buttons.add(Buttons.green(
                    player.getFinsFactionCheckerPrefix() + "zephyrionbtAttach_" + agentID + "_" + opponentFaction,
                    "Attach " + agentName));
            buttons.add(Buttons.red(
                    player.getFinsFactionCheckerPrefix() + "zephyrionbtPurge_" + agentID + "_" + opponentFaction,
                    "Purge & Exhaust Opponent's Agent"));
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + " has no unused agents available to draw via _Subdue Chancellor_.");
            buttons.add(Buttons.red(
                    player.getFinsFactionCheckerPrefix() + "zephyrionbtExhaustOp_" + opponentFaction,
                    "Exhaust Opponent's Agent"));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", please choose how to resolve _Subdue Chancellor_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("zephyrionbtAttach_")
    static void zephyrionbtAttach(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephyrionbtAttach_", "");
        String agentID = buttonID.split("_")[0];

        player.addLeader(agentID);
        game.addFakeAgent(agentID);

        LeaderModel attachedModel = Mapper.getLeader(agentID);
        String agentName = attachedModel != null ? attachedModel.getName() : agentID;
        String factionLabel =
                attachedModel != null ? ", the " + capitalize(attachedModel.getFaction()) + " agent," : "";
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " attached _" + agentName + "_" + factionLabel
                        + " to _Subdue Chancellor_ and may now treat it as their own agent.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("zephyrionbtPurge_")
    static void zephyrionbtPurge(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephyrionbtPurge_", "");
        String agentID = buttonID.split("_")[0];
        String opponentFaction = buttonID.split("_")[1];

        game.addFakeAgent(agentID);
        LeaderModel purgedModel = Mapper.getLeader(agentID);
        String agentName = purgedModel != null ? purgedModel.getName() : agentID;
        String factionLabel = purgedModel != null ? ", the " + capitalize(purgedModel.getFaction()) + " agent," : "";
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " purged _" + agentName + "_" + factionLabel
                        + " while resolving _Subdue Chancellor_.");

        resolveExhaustOpponent(event, game, player, opponentFaction);
    }

    @ButtonHandler("zephyrionbtExhaustOp_")
    static void zephyrionbtExhaustOp(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephyrionbtExhaustOp_", "");
        resolveExhaustOpponent(event, game, player, buttonID);
    }

    private static void resolveExhaustOpponent(
            ButtonInteractionEvent event, Game game, Player player, String opponentFaction) {
        Player opponent = game.getPlayerFromColorOrFaction(opponentFaction);
        if (opponent == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find opponent, please resolve manually.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Leader> unexhaustedAgents = opponent.getLeaders().stream()
                .filter(l -> Constants.AGENT.equals(l.getType()) && !l.isExhausted())
                .toList();

        if (unexhaustedAgents.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " all of " + opponent.getRepresentationNoPing()
                            + "'s agents are already exhausted. Please resolve manually if needed.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (unexhaustedAgents.size() == 1) {
            ExhaustLeaderService.exhaustLeader(game, opponent, unexhaustedAgents.getFirst());
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " exhausted " + opponent.getRepresentationNoPing()
                            + "'s agent via _Subdue Chancellor_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Leader agent : unexhaustedAgents) {
            String agentName = Mapper.getLeader(agent.getId()) != null
                    ? Mapper.getLeader(agent.getId()).getName()
                    : agent.getId();
            buttons.add(Buttons.red(
                    player.getFinsFactionCheckerPrefix() + "zephyrionbtExhaustAgent_" + opponentFaction + "_"
                            + agent.getId(),
                    "Exhaust " + agentName));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", " + opponent.getRepresentationNoPing()
                        + " has multiple unexhausted agents. Choose which to exhaust.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("zephyrionbtExhaustAgent_")
    static void zephyrionbtExhaustAgent(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        buttonID = buttonID.replace("zephyrionbtExhaustAgent_", "");
        String opponentFaction = buttonID.split("_")[0];
        String agentID = buttonID.split("_")[1];

        Player opponent = game.getPlayerFromColorOrFaction(opponentFaction);
        if (opponent == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find opponent, please resolve manually.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Leader agent = opponent.getLeader(agentID).orElse(null);
        if (agent == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find agent, please resolve manually.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExhaustLeaderService.exhaustLeader(game, opponent, agent);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted " + opponent.getRepresentationNoPing()
                        + "'s agent via _Subdue Chancellor_.");
        ButtonHelper.deleteMessage(event);
    }
}
