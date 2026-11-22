package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.UnusedCommanderHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;

@UtilityClass
class BrokeredAllianceAcd2ButtonHandler {

    @ButtonHandler("resolveBrokeredAlliance")
    public static void resolveBrokeredAlliance(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == player) continue;
            buttons.add(FoWHelper.fogSafeTargetButton(
                    player.factionButtonChecker() + "brokeredAllianceTarget_" + target.getColor(),
                    "green",
                    target,
                    player));
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "There is no other player to target with _Brokered Alliance_.");
            return;
        }
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the player who is resolving the strategic action. They will gain the alliance"
                        + " ability of a random, unused faction.",
                buttons);
    }

    @ButtonHandler("brokeredAllianceTarget_")
    public static void resolveBrokeredAllianceTarget(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring("brokeredAllianceTarget_".length()));
        if (target == null || target == player || !game.getRealPlayers().contains(target)) {
            return;
        }
        ButtonHelper.deleteMessage(event);

        int oldStrategicCC = player.getStrategicCC();
        player.setStrategicCC(oldStrategicCC + 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " placed 1 command token from their reinforcements in their strategy pool ("
                        + oldStrategicCC + "->" + player.getStrategicCC() + ") using _Brokered Alliance_.");

        String commander = UnusedCommanderHelper.getUnusedCommander(game);
        if (commander.isBlank()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "There are no unused alliance abilities available for _Brokered Alliance_.");
            return;
        }
        target.addLeader(commander);
        target.getLeader(commander).ifPresent(leader -> leader.setLocked(false));
        game.addFakeCommander(commander);
        MessageHelper.sendMessageToChannelWithEmbed(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + " gained the alliance ability of "
                        + Mapper.getLeader(commander).getName() + " through _Brokered Alliance_.",
                Mapper.getLeader(commander).getRepresentationEmbed());
    }
}
