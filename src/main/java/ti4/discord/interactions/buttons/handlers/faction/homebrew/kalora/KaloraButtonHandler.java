package ti4.discord.interactions.buttons.handlers.faction.homebrew.kalora;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class KaloraButtonHandler {

    @ButtonHandler("exhaustAgent_kaloraagent")
    public static void exhaustKaloraAgent(ButtonInteractionEvent event, Game game, Player player) {
        var leader = player.getLeader("kaloraagent").orElse(null);
        if (leader == null) return;
        ExhaustLeaderService.exhaustLeader(game, player, leader);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getRepresentation() + " has exhausted Valzor, the Kalora agent.");
        List<Button> buttons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please use the buttons to gain 1 command token.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("kaloraExploreFront_")
    public static void kaloraExploreFront(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos = buttonID.replace("kaloraExploreFront_", "");
        ExploreService.expFront(event, game.getTileByPosition(pos), game, player, true, null);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void offerMechButtons(Player player, Game game, Tile tile) {
        for (UnitHolder uH : tile.getPlanetUnitHolders()) {
            int mechCount = uH.getUnitCount(UnitType.Mech, player.getColor());
            if (mechCount == 0) continue;
            if (!game.getStoredValue("planetsTakenThisRound").contains(uH.getName())) continue;
            List<Button> buttons = new ArrayList<>();
            for (Tile t : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
                if (t.getPlanetUnitHolders().isEmpty()) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "kaloraExploreFront_" + t.getPosition(),
                            t.getRepresentationForButtons(game, player),
                            ExploreEmojis.Frontier));
                }
            }
            if (!buttons.isEmpty()) {
                buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Delete these buttons"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", please explore systems equal to the number of mechs you have here.",
                        buttons);
            }
        }
    }
}
