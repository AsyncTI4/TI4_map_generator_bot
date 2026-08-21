package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class NetrunnersBreakthroughHandler {
    public static boolean hasDataBreachToken(Game game, Player target, String breakthroughId) {
        if (game == null || target == null || breakthroughId == null) return false;
        return game.getRealPlayers().stream()
                .filter(netrunner -> netrunner.hasUnlockedBreakthrough("netrunnersbt"))
                .map(netrunner -> game.getStoredValue("netrunnersDataBreach" + netrunner.getFaction()))
                .anyMatch(placement -> (target.getFaction() + "~" + breakthroughId).equals(placement));
    }

    public static void offerDataBreachPlacement(Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("netrunnersbt")) {
            return;
        }
        List<Button> buttons = getDataBreachPlacementButtons(game, player);
        if (buttons.isEmpty()) return;
        String message =
                player.getRepresentationUnfogged() + ", please choose the breakthrough for your **Data Breach** token.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersDataBreach_";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), message, NewStuffHelper.buttonPagination(buttons, buttonPrefix, 0));
    }

    private static List<Button> getDataBreachPlacementButtons(Game game, Player player) {
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .flatMap(other -> other.getBreakthroughIDs().stream()
                        .filter(bt -> Mapper.getBreakthrough(bt) != null)
                        .map(bt -> Buttons.green(
                                player.factionButtonChecker() + "netrunnersDataBreach_" + other.getFaction() + "_" + bt,
                                "Place Token: " + other.getColorDisplayName() + " — "
                                        + Mapper.getBreakthrough(bt)
                                                .getShortName()
                                                .replace("\n", " "),
                                FactionEmojis.netrunners)))
                .toList();
        return buttons;
    }

    @ButtonHandler("netrunnersDataBreach_")
    public static void resolveDataBreachPlacement(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("netrunnersbt")) {
            return;
        }
        List<Button> buttons = getDataBreachPlacementButtons(game, player);
        String message =
                player.getRepresentationUnfogged() + ", please choose the breakthrough for your **Data Breach** token.";
        String buttonPrefix = player.factionButtonChecker() + "netrunnersDataBreach_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) return;
        String[] parts = buttonID.replace("netrunnersDataBreach_", "").split("_", 2);
        if (parts.length != 2) return;
        Player other = game.getPlayerFromColorOrFaction(parts[0]);
        if (other == null || !other.hasBreakthrough(parts[1]) || Mapper.getBreakthrough(parts[1]) == null) return;
        game.setStoredValue("netrunnersDataBreach" + player.getFaction(), other.getFaction() + "~" + parts[1]);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " placed their Data Breach token on "
                        + Mapper.getBreakthrough(parts[1]).getNameRepresentation() + ".");
    }
}
