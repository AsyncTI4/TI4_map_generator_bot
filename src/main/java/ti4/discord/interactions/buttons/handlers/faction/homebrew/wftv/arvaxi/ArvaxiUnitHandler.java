package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.arvaxi;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;

@UtilityClass
public class ArvaxiUnitHandler {

    public static void offerWrathOfTheCovenantDiscard(Game game, Player player, Tile tile) {
        for (Player arvaxi : game.getRealPlayers()) {
            if (arvaxi == player || !arvaxi.hasUnit("arvaxi_flagship")) {
                continue;
            }
            boolean flagshipHere = tile.getUnitHolders().values().stream()
                    .anyMatch(holder -> holder.getUnitCount(UnitType.Flagship, arvaxi) > 0);
            if (!flagshipHere) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + ", "
                            + FoWHelper.identityOrColorIfCanSeeStats(
                                    game, arvaxi, player, arvaxi.getRepresentationNoPing())
                            + "'s _Wrath of the Covenant_ is in this system. You must discard 1 action card to"
                            + " activate it.",
                    ActionCardHelper.getDiscardActionCardButtons(player, false));
        }
    }
}
