package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class TaBreakthroughHandler {

    private static final String SAFE_HAVENS = "tabt";
    private static final String SAFE_HAVENS_PLACE = "taSafeHavensPlace_";
    private static final String SAFE_HAVENS_USED = "taSafeHavensUsed_";

    public static void offerSafeHavensInfantry(Game game, Player player, String planetName) {
        if (game == null
                || player == null
                || planetName == null
                || !player.hasUnlockedBreakthrough(SAFE_HAVENS)
                || !game.getStoredValue(SAFE_HAVENS_USED + player.getFaction()).isEmpty()) {
            return;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        if (planet == null || !TaAbilityHandler.planetHasAnyAttachment(tile, planetName)) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + SAFE_HAVENS_PLACE + tile.getPosition() + "|" + planetName,
                        "Place 1 Infantry",
                        player.getFactionEmoji()),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", you may place 1 infantry on "
                        + Helper.getPlanetRepresentation(planetName, game) + " with **Safe Havens**.",
                buttons);
    }

    @ButtonHandler(SAFE_HAVENS_PLACE)
    public static void placeSafeHavensInfantry(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.substring(SAFE_HAVENS_PLACE.length()).split("\\|", 2);
        Tile tile = game == null || parts.length != 2 ? null : game.getTileByPosition(parts[0]);
        Planet planet = tile == null || parts.length != 2 ? null : tile.getUnitHolderFromPlanet(parts[1]);
        if (game == null
                || player == null
                || parts.length != 2
                || planet == null
                || !player.hasUnlockedBreakthrough(SAFE_HAVENS)
                || !game.getStoredValue(SAFE_HAVENS_USED + player.getFaction()).isEmpty()
                || !TaAbilityHandler.planetHasAnyAttachment(tile, parts[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + parts[1]);
        game.setStoredValue(SAFE_HAVENS_USED + player.getFaction(), "used");
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " placed 1 infantry on "
                        + Helper.getPlanetRepresentation(parts[1], game) + " with **Safe Havens**.");
    }

    public static boolean canUseSafeHavensCoexistence(Game game, Player player, String planetName) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough(SAFE_HAVENS)) {
            return false;
        }
        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = tile == null ? null : tile.getUnitHolderFromPlanet(planetName);
        return planet != null
                && player.getPlanets().contains(planetName)
                && TaAbilityHandler.planetHasAnyAttachment(tile, planetName);
    }

    public static void clearSafeHavens(Game game) {
        if (game == null) {
            return;
        }
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(SAFE_HAVENS_USED + player.getFaction());
        }
    }
}
