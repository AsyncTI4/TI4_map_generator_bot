package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.arvaxi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class ArvaxiAgentButtonHandler {

    public static void postInitialButtons(Game game, Player discarder, String acID) {
        if (ActionCardHelper.isSabotageOrShatter(acID)) return;
        Integer acIndex = game.getDiscardActionCards().get(acID);
        if (acIndex == null) return;
        String acName = Mapper.getActionCard(acID).getName();
        for (Player p : game.getRealPlayers()) {
            if (!p.hasUnexhaustedLeader("arvaxiagent")) continue;
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.gray(
                    p.factionButtonChecker() + "arvaxiAgentOffer_" + acIndex + "_" + discarder.getFaction(),
                    "Use Arvaxi Agent on " + acName,
                    CardEmojis.getACEmoji(game)));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    p.getCardsInfoThread(),
                    p.getRepresentationUnfoggedNoPing() + ", " + discarder.getFactionNameOrColor() + " discarded _"
                            + acName + "_. You may exhaust Marik, the Wise to allow a player other than **"
                            + discarder.getFactionNameOrColor() + "** to spend 3 influence to retrieve it.",
                    buttons);
        }
    }

    @ButtonHandler("arvaxiAgentOffer_")
    public static void arvaxiAgentOffer(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String stripped = buttonID.replace("arvaxiAgentOffer_", "");
        int acIndex = Integer.parseInt(stripped.split("_")[0]);
        String discarderFaction = stripped.split("_")[1];

        String acID = ActionCardHelper.getDiscardedAcID(game, acIndex);
        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Action card no longer in discard pile.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String acName = Mapper.getActionCard(acID).getName();
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target.getFaction().equals(discarderFaction)) continue;
            buttons.add(Buttons.green(
                    "FFCC_" + player.getFaction() + "_arvaxiAgentPickTarget_" + acIndex + "_" + target.getFaction(),
                    target.getFactionNameOrColor(),
                    target.fogSafeEmoji()));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "No eligible players found.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose a player to receive _" + acName + "_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arvaxiAgentPickTarget_")
    public static void arvaxiAgentPickTarget(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String stripped = buttonID.replace("arvaxiAgentPickTarget_", "");
        int acIndex = Integer.parseInt(stripped.split("_")[0]);
        String targetFaction = stripped.split("_")[1];
        Player target = game.getPlayerFromColorOrFaction(targetFaction);
        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find target, please resolve manually.");
            return;
        }
        String acID = ActionCardHelper.getDiscardedAcID(game, acIndex);
        String acName = acID != null ? Mapper.getActionCard(acID).getName() : "the action card";

        player.getLeader("arvaxiagent").ifPresent(agent -> ExhaustLeaderService.exhaustLeader(game, player, agent));

        game.pickActionCard(target.getUserID(), acIndex);
        ActionCardHelper.sendActionCardInfo(game, target);

        List<Button> spendButtons = ButtonHelper.getExhaustButtonsWithTG(game, target, "inf");
        spendButtons.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCardsInfoThread(),
                target.getRepresentationUnfogged()
                        + ", Marik, the Wise, the Arvaxi agent has been used. You have received _" + acName
                        + "_. Please spend 3 influence.",
                spendButtons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " used "
                        + Mapper.getLeader("arvaxiagent").getNameRepresentation()
                        + " to allow " + target.getRepresentationNoPing() + " to retrieve _" + acName
                        + "_ " + CardEmojis.getACEmoji(game) + " from the discard pile.");
        ButtonHelper.deleteMessage(event);
    }
}
