package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

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
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class OnyxxaUnitHandler {

    public static int getObeliskCombatModifier(Player player, UnitHolder unitHolder) {
        int totalMechs = unitHolder.getUnitCount(UnitType.Mech, player.getColor());
        return Math.max(0, totalMechs - 1);
    }

    public static boolean isFlagshipInOrAdjacentSystem(Game game, Player player, Tile tile) {
        if (tile == null) {
            return false;
        }
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true).stream()
                .map(game::getTileByPosition)
                .anyMatch(nearbyTile ->
                        nearbyTile != null && ButtonHelper.doesPlayerHaveFSHere("onyxxa_flagship", player, nearbyTile));
    }

    public static void offerFlagshipWinButton(Player player, String msg) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.gray(
                player.factionButtonChecker() + "onyxxaFlagshipWin",
                "Spend 1 Influence for Command Token",
                FactionEmojis.onyxxa));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                msg
                        + ", a reminder that if you win this space combat, you may spend 1 influence to gain 1 command token.",
                buttons);
    }

    @ButtonHandler("onyxxaFlagshipWin")
    public static void onyxxaFlagshipWin(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> spendButtons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Please spend 1 influence.", spendButtons);
        List<Button> ccButtons = ButtonHelper.getGainCCButtons(player);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", your current command tokens are "
                        + player.getCCRepresentation()
                        + ". Use buttons to gain 1 command token.",
                ccButtons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
