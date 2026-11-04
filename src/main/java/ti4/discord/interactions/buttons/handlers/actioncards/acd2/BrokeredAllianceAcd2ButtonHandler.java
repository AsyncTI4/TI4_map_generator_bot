package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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
        String key = pendingAllyKey(player);
        String commander = game.getStoredValue(key);
        if (commander.isEmpty()) {
            if (player.getTg() < 3) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), "You need 3 trade goods to resolve _Brokered Alliance_.");
                return;
            }
            commander = UnusedCommanderHelper.getUnusedCommander(game);
            if (commander.isBlank()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        "There are no unused alliance abilities available for _Brokered Alliance_.");
                return;
            }
            player.setTg(player.getTg() - 3);
            game.setStoredValue(key, commander);
        }
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " spent 3 trade goods and revealed this alliance ability for"
                        + " _Brokered Alliance_.",
                Mapper.getLeader(commander).getRepresentationEmbed());
        List<Button> buttons = game.getRealPlayers().stream()
                .map(target -> FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + "brokeredAllianceTarget_" + target.getColor(),
                        "green",
                        target,
                        player))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a player to gain this alliance ability.",
                buttons);
    }

    @ButtonHandler("brokeredAllianceTarget_")
    public static void resolveBrokeredAllianceTarget(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring("brokeredAllianceTarget_".length()));
        String commander = game.getStoredValue(pendingAllyKey(player));
        if (target == null || !game.getRealPlayers().contains(target) || commander.isEmpty()) {
            return;
        }
        target.addLeader(commander);
        target.getLeader(commander).ifPresent(leader -> leader.setLocked(false));
        game.addFakeCommander(commander);
        game.removeStoredValue(pendingAllyKey(player));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + " gained the alliance ability of "
                        + Mapper.getLeader(commander).getName() + " through _Brokered Alliance_.");
    }

    private static String pendingAllyKey(Player player) {
        return "brokeredAlliancePending_" + player.getUserID();
    }
}
