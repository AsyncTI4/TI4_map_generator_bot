package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Xytheris;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class XytherisTechHandler {
    private static final String BIONUTRIENTS = "thxytherisr";
    private static final String USE_BIOM = "useBiomechanicalNutrients_";
    private static final String SELECT_BIOM_SYSTEM = "selectBiomechanicalSystem_";
    private static final String SELECT_BIOM_SHIP = "selectBiomechanicalShip_";

    public static Button getBiomechanicalButton(GenericInteractionCreateEvent event, Game game, Player player, int h) {
        return Buttons.green(
                player.factionButtonChecker() + USE_BIOM + h, "Use Biomechanical Nutrients", FactionEmojis.xytheris);
    }

    @ButtonHandler(USE_BIOM)
    public static void selectBioMechanicalNutrientsSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTech(BIONUTRIENTS)) {
            return;
        }

        String hits = buttonID.replace(USE_BIOM, "");
        int pageMarker = hits.indexOf("_page");
        if (pageMarker >= 0) {
            hits = hits.substring(0, pageMarker);
        }
        int h = hits.isEmpty() ? 0 : Integer.parseInt(hits);

        List<Button> buttons = new ArrayList<>();

        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game).stream()
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_BIOM_SYSTEM + tile.getPosition() + "|" + h,
                    tile.getRepresentationForButtons(game, player)));
        }

        String message = "Please choose the system in which to place the unit.";
        String buttonPrefix = player.factionButtonChecker() + USE_BIOM + h + "_";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, buttonPrefix, buttonID)) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(buttons, null, buttonPrefix, 24, 0, true));

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_BIOM_SYSTEM)
    public static void selectBiomechanicalNutrientsShip(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTech(BIONUTRIENTS)) {
            return;
        }

        String payload = buttonID.replace(SELECT_BIOM_SYSTEM, "");
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }

        String tilePos = parts[0];
        String hits = parts[1];

        Tile tile = game.getTileByPosition(tilePos);
        int h = hits.isEmpty() ? 0 : Integer.parseInt(hits);

        List<Button> buttons = player.getUnitModels().stream()
                .filter(unit -> unit.getIsShip())
                .filter(unit -> unit.getCost() < h)
                .sorted(Comparator.comparing(UnitModel::getName))
                .map(unit -> Buttons.green(
                        player.factionButtonChecker() + SELECT_BIOM_SHIP + tile.getPosition() + "|" + unit.getAsyncId(),
                        "Place " + unit.getName(),
                        unit.getUnitEmoji()))
                .toList();

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please choose the ship to place in this system.", buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_BIOM_SHIP)
    public static void resolveBiomechanicalNutrients(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasTech(BIONUTRIENTS)) {
            return;
        }

        String payload = buttonID.replace(SELECT_BIOM_SHIP, "");
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }

        String tilePos = parts[0];
        String asyncId = parts[1];

        Tile tile = game.getTileByPosition(tilePos);

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 " + asyncId);
        ButtonHelper.deleteMessage(event);
    }
}
