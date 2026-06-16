package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class IronBreakthroughHandler {

    private static final String IRON_BT = "ironbt";

    public static void sendIronBtMessage(Player player, Game game) {
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.getRepresentation()
                        + " **REMINDER**: You do not need a mech on the planet due to _Foundry Network_.");
    }

    public static List<Button> getPlaceUnitButtonsForIronBt(
            Player player, Tile origTile, Game game, String placePrefix) {
        List<Button> unitButtons = new ArrayList<>();

        if (player.hasUnlockedBreakthrough(IRON_BT)) {
            for (Tile tile :
                    CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Spacedock)) {
                if (tile.getPosition().equalsIgnoreCase(origTile.getPosition())
                        || FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                    continue;
                }
                for (UnitHolder uH : tile.getUnitHolders().values()) {
                    if (player.getUnitsOwned().contains("spacedock")
                            || player.getUnitsOwned().contains("spacedock2")
                            || uH.getUnitCount(UnitType.Spacedock, player) > 0) {
                        if (uH instanceof Planet planet) {
                            if (player.getPlanetsAllianceMode().contains(uH.getName())) {
                                String pp = planet.getName();
                                Button inf1Button = Buttons.green(
                                        player.factionButtonChecker() + placePrefix + "_mech_" + pp,
                                        "Produce 1 Mech on " + Helper.getPlanetRepresentation(pp, game),
                                        FactionEmojis.iron);
                                unitButtons.add(inf1Button);
                            }
                        } else {
                            Button inf1Button = Buttons.green(
                                    player.factionButtonChecker() + placePrefix + "_mech_space" + tile.getPosition(),
                                    "Produce 1 Mech in " + tile.getPosition() + " space",
                                    FactionEmojis.iron);
                            unitButtons.add(inf1Button);
                        }
                    }
                }
            }
        }

        return unitButtons;
    }
}
