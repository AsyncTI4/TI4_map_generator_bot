package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.leader.ExhaustLeaderService;
import ti4.service.leader.RefreshLeaderService;

@UtilityClass
class SimulacrumAcd2ButtonHandler {

    @ButtonHandler("resolveSimulacrum")
    public static void resolveSimulacrum(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            for (String leaderID : p2.getLeaderIDs()) {
                var leaderModel = Mapper.getLeader(leaderID);
                if (leaderModel == null || !"agent".equals(leaderModel.getType())) {
                    continue;
                }

                Leader agent = p2.getLeader(leaderID).orElse(null);
                if (agent == null) {
                    continue;
                }

                String buttonPrefix = agent.isExhausted() ? "Ready " : "Exhaust ";
                buttons.add(Buttons.gray(
                        "simulacrumToggleAgent_" + p2.getFaction() + "_" + leaderID,
                        buttonPrefix + leaderModel.getName()));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose an agent to ready or exhaust.",
                buttons);
    }

    @ButtonHandler("simulacrumToggleAgent_")
    public static void resolveSimulacrumToggleAgent(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] buttonParts = buttonID.split("_", 3);
        if (buttonParts.length < 3) {
            return;
        }
        String faction = buttonParts[1];
        String agentID = buttonParts[2];
        Player agentOwner = game.getPlayerFromColorOrFaction(faction);
        if (agentOwner == null) {
            return;
        }

        Leader agent = agentOwner.getLeader(agentID).orElse(null);
        if (agent == null) {
            return;
        }

        String ownerName = Mapper.getLeader(agentID).getName() + " (" + agentOwner.getRepresentationNoPing() + ")";
        if (agent.isExhausted()) {
            RefreshLeaderService.refreshLeader(agentOwner, agent, game);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.toString() + " readied " + ownerName + " using _Simulacrum_.");
        } else {
            ExhaustLeaderService.exhaustLeader(game, agentOwner, agent);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.toString() + " exhausted " + ownerName + " using _Simulacrum_.");
        }

        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
