package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.unit.AddUnitService;

@UtilityClass
class BoardingPartyAcd2ButtonHandler {

    @ButtonHandler("resolveBoardingParty")
    public static void resolveBoardingParty(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) return;
        List<Button> buttons = getEligibleShips(player, game, tile).stream()
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + "boardingPartyPlace_" + tile.getPosition() + "_"
                                + unit.getAsyncId(),
                        unit.getName()))
                .toList();
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose the type of ship you destroyed (cost 4 or less) to place 1 ship"
                        + " of that type from your reinforcements.",
                buttons);
    }

    @ButtonHandler("boardingPartyPlace_")
    public static void resolveBoardingPartyPlace(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.substring("boardingPartyPlace_".length()).split("_", 2);
        if (parts.length != 2) return;
        Tile tile = game.getTileByPosition(parts[0]);
        if (tile == null) return;
        UnitModel unit = getEligibleShips(player, game, tile).stream()
                .filter(model -> model.getAsyncId().equals(parts[1]))
                .findFirst()
                .orElse(null);
        if (unit == null) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + unit.getAsyncId());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " placed 1 " + unit.getName() + " for _Boarding Party_.");
    }

    static List<UnitModel> getEligibleShips(Player player, Game game, Tile tile) {
        Set<UnitType> destroyedShipTypes = game.getRealPlayers().stream()
                .filter(opponent -> !opponent.getUserID().equals(player.getUserID()))
                .flatMap(opponent -> Acd2UnitHelper.getUnitsWithCostAtMost(opponent, tile, 4).stream())
                .filter(UnitModel::getIsShip)
                .map(UnitModel::getUnitType)
                .collect(Collectors.toSet());
        return Acd2UnitHelper.getUnitsWithCostAtMost(player, tile, Integer.MAX_VALUE).stream()
                .filter(unit -> unit.getIsShip() && destroyedShipTypes.contains(unit.getUnitType()))
                .toList();
    }
}
