package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

@UtilityClass
class AbductionAcd2ButtonHandler {

    @ButtonHandler("resolveAbduction")
    public static void resolveAbduction(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers(true)) {
            if (p2 == player) continue;
            for (Leader leader : p2.getLeaders()) {
                boolean isAgent = Constants.AGENT.equals(leader.getType());
                boolean isUnlockedCommander = Constants.COMMANDER.equals(leader.getType()) && !leader.isLocked();
                if (!isAgent && !isUnlockedCommander) {
                    continue;
                }
                String label = leader.getName() + (game.isFowMode() ? "" : " (" + p2.getFactionModel().getShortName()
                        + ")");
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "abductionTake_" + p2.getFaction() + "_" + leader.getId(),
                        label));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", none of your neighbors have an agent or unlocked commander to take for _Abduction_.");
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose 1 of your neighbors' agents or commanders to take for _Abduction_. Remember to"
                        + " return it to that player at the end of this turn.",
                buttons);
    }

    @ButtonHandler("abductionTake_")
    public static void resolveAbductionTake(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("abductionTake_", "").split("_", 2);
        ButtonHelper.deleteMessage(event);
        if (parts.length < 2) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        Leader leader = target == null ? null : target.unsafeGetLeader(parts[1]);
        if (target == null || leader == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Abduction_.");
            return;
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " took " + target.getRepresentationNoPing() + "'s " + leader.getName()
                        + " (" + leader.getType() + ") for _Abduction_. " + player.getRepresentationNoPing()
                        + " may use its ability this turn; return it to " + target.getRepresentationNoPing()
                        + " at the end of this turn.");
    }
}
