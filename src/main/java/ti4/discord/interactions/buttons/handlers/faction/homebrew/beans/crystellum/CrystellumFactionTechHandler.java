package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

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
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class CrystellumFactionTechHandler {
    private static final String MOLECULAR_BINDING = "becrystmb";
    private static final String USE_MB = "crystellumUseMolecularBinding_";
    private static final String FF_TO_DD = "crystellumFighterToDestroyer_";
    private static final String INF_TO_MF = "crystellumInfantryToMech_";

    public static Button getMolecularBindingButton(Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_MB, "Use Molecular Binding", FactionEmojis.crystellum);
    }

    @ButtonHandler(USE_MB)
    public static void resolveUseMolecularBinding(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null || player == null || game == null) {
            return;
        }
        if (!player.hasTech(MOLECULAR_BINDING)) {
            return;
        }
        if (!player.isActivePlayer()) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + FF_TO_DD, "Convert Fighter to Destroyer"));
        buttons.add(Buttons.green(player.factionButtonChecker() + INF_TO_MF, "Convert Infantry to Mech"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.toString()
                        + ", choose what you want to convert. Eligible tiles will be shown afterwards."
                        + "\n"
                        + "**REMINDER**: This can only be used once per turn at the start of your turn.",
                buttons);
    }

    @ButtonHandler(FF_TO_DD)
    public static void resolveFighterToDestroyerChoice(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null || player == null || game == null) {
            return;
        }
        if (!player.hasTech(MOLECULAR_BINDING)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.isActivePlayer()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Tile> fighterTiles = new ArrayList<>();
        for (Tile tile : game.getTiles()) {
            UnitHolder space = tile.getSpaceUnitHolder();
            if (space == null) {
                continue;
            }

            int fighters = space.getUnitCount(Units.getUnitKey(UnitType.Fighter, player.getColor()));
            if (fighters > 0) {
                fighterTiles.add(tile);
            }
        }

        if (fighterTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "There are no eligible tiles to convert a fighter to a destroyer.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : fighterTiles) {
            String tilePos = tile.getPosition();
            String label = tile.toString();
            String buttonId = "crystellumFFDDTile_" + tilePos;

            buttons.add(Buttons.green(player.factionButtonChecker() + buttonId, label));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.toString() + ", please choose the system in which to convert 1 fighter to a destroyer.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("crystellumFFDDTile_")
    public static void resolveFighterToDestroyer(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            return;
        }
        if (!player.hasTech(MOLECULAR_BINDING)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.isActivePlayer()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String pos = buttonID.replace("crystellumFFDDTile_", "");
        Tile tile = game.getTileByPosition(pos);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find selected tile.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        var removedUnits = RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 fighter");
        int removedFighters = removedUnits.stream()
                .mapToInt(RemoveUnitService.RemovedUnit::getTotalRemoved)
                .sum();
        if (removedFighters < 1) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    player.toString() + " no longer has an eligible fighter in " + tile.toString() + ".");
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 destroyer");

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.toString()
                        + " replaced 1 fighter in "
                        + tile.toString()
                        + " with 1 destroyer form their reinforcements using _Molecular Binding_.");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(INF_TO_MF)
    public static void resolveInfantryToMechChoice(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null || player == null || game == null) {
            return;
        }
        if (!player.hasTech(MOLECULAR_BINDING)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.isActivePlayer()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> eligiblePlanets = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Tile tile = game.getTileContainingPlanet(planetName);
            if (tile == null) {
                continue;
            }

            UnitHolder holder = tile.getPlanet(planetName);
            if (holder == null) {
                continue;
            }

            int infantry = holder.getUnitCount(Units.getUnitKey(UnitType.Infantry, player.getColor()));
            if (infantry >= 2) {
                eligiblePlanets.add(planetName);
            }
        }

        if (eligiblePlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(), "There are no eligible planets to convert infantry to a mech.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String planetName : eligiblePlanets) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "crystellumINFMFPlanet_" + planetName,
                    Mapper.getPlanet(planetName).getName()));
        }

        MessageHelper.sendMessageToChannelWithButtons(
                game.getActionsChannel(),
                player.toString() + ", please choose the planet on which you will replace 2 infantry with a mech.",
                buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("crystellumINFMFPlanet_")
    public static void resolveInfantryToMech(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (event == null || player == null || game == null) {
            return;
        }
        if (!player.hasTech(MOLECULAR_BINDING)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!player.isActivePlayer()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planet = buttonID.replace("crystellumINFMFPlanet_", "");
        Tile tile = game.getTileContainingPlanet(planet);
        if (planet == null || tile == null) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Unable to locate chosen planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        var removedUnits = RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "2 infantry " + planet);
        int removedInfantry = removedUnits.stream()
                .mapToInt(RemoveUnitService.RemovedUnit::getTotalRemoved)
                .sum();
        if (removedInfantry < 2) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    player.toString() + " no longer has 2 eligible infantry on "
                            + Mapper.getPlanet(planet).getNameRepresentation() + ".");
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);

        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                player.toString()
                        + " replaced 2 infantry on "
                        + Mapper.getPlanet(planet).getNameRepresentation()
                        + " with 1 mech from their reinforcements using _Molecular Binding_.");

        ButtonHelper.deleteMessage(event);
    }
}
