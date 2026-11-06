package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
class SettlersAcd2ButtonHandler {

    private static final UnitType[] GROUND_UNITS = {UnitType.Infantry, UnitType.Mech, UnitType.Pds, UnitType.Spacedock};

    @ButtonHandler("resolveSettlers")
    public static void resolveSettlers(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != player && !settlersPlanets(game, target).isEmpty()) {
                buttons.add(FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + "settlersPlayer_" + target.getFaction(), "gray", target));
            }
        }
        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", there are no non-home planets containing another player's units for _Settlers_.");
            return;
        }
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose another player to target with _Settlers_.",
                buttons);
    }

    @ButtonHandler("settlersPlayer_")
    public static void resolveSettlersPlayer(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("settlersPlayer_", ""));
        ButtonHelper.deleteMessage(event);
        if (target == null || target == player) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Settlers_.");
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (String planet : settlersPlanets(game, target)) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "settlersChoose_" + target.getFaction() + "_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no units on a non-home planet to target with _Settlers_.");
            return;
        }
        buttons.add(Buttons.DONE_RED_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose a non-home planet that contains "
                        + target.getRepresentationNoPing() + "'s units for _Settlers_.",
                buttons);
    }

    @ButtonHandler("settlersChoose_")
    public static void resolveSettlersChoose(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("settlersChoose_", "").split("_", 2);
        Player target = parts.length < 2 ? null : game.getPlayerFromColorOrFaction(parts[0]);
        String planet = parts.length < 2 ? null : parts[1];
        ButtonHelper.deleteMessage(event);
        if (target == null || target == player || !settlersPlanets(game, target).contains(planet)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Settlers_ there.");
            return;
        }

        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(
                target.factionButtonChecker() + "settlersConsent_allow_" + player.getFaction() + "_" + planet,
                "Allow 2 Infantry into Coexistence"));
        buttons.add(Buttons.red(
                target.factionButtonChecker() + "settlersConsent_refuse_" + player.getFaction() + "_" + planet,
                "Refuse (Exhaust & Destroy up to 2)"));
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationUnfogged() + ", " + player.getRepresentationNoPing()
                        + " played _Settlers_ targeting your units on " + Helper.getPlanetRepresentation(planet, game)
                        + ". Will you allow them to place 2 infantry into coexistence?",
                buttons);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", waiting for " + target.getRepresentationNoPing()
                        + " to decide on _Settlers_.");
    }

    @ButtonHandler("settlersConsent_")
    public static void resolveSettlersConsent(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("settlersConsent_", "").split("_", 3);
        if (parts.length < 3) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String choice = parts[0];
        Player acting = game.getPlayerFromColorOrFaction(parts[1]);
        String planet = parts[2];
        Tile tile = game.getTileFromPlanet(planet);
        ButtonHelper.deleteMessage(event);
        if (acting == null || tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Settlers_.");
            return;
        }

        if ("allow".equals(choice)) {
            game.setStoredValue("coexistFlag", "yes");
            AddUnitService.addUnits(event, tile, game, acting.getColor(), "2 infantry " + planet);
            game.removeStoredValue("coexistFlag");
            ButtonHelperAbilities.oceanBoundCheck(game);
            MessageHelper.sendMessageToChannel(
                    acting.getCorrectChannel(),
                    player.getRepresentationNoPing() + " allowed _Settlers_: " + acting.getRepresentationNoPing()
                            + " placed 2 infantry into coexistence on " + Helper.getPlanetRepresentation(planet, game)
                            + ".");
            return;
        }

        Player controller = game.getPlayerThatControlsPlanet(planet);
        if (controller != null) {
            controller.exhaustPlanet(planet);
        }
        MessageHelper.sendMessageToChannel(
                acting.getCorrectChannel(),
                player.getRepresentationNoPing() + " refused _Settlers_ and exhausted "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        sendSettlersDestroyButtons(acting, game, player, planet, 2);
    }

    @ButtonHandler("settlersDestroy_")
    public static void resolveSettlersDestroy(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("settlersDestroy_", "").split("_", 4);
        if (parts.length < 4) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int remaining;
        try {
            remaining = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(parts[1]);
        UnitType type = UnitType.valueOf(parts[2]);
        String planet = parts[3];
        Tile tile = game.getTileFromPlanet(planet);
        Planet uH = game.getUnitHolderFromPlanet(planet);
        ButtonHelper.deleteMessage(event);
        if (target == null || tile == null || uH == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Settlers_.");
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, target, uH, type, 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " destroyed 1 " + type.humanReadableName() + " belonging to "
                        + target.getRepresentationNoPing() + " on " + Helper.getPlanetRepresentation(planet, game)
                        + " for _Settlers_.");

        if (remaining > 1) {
            sendSettlersDestroyButtons(player, game, target, planet, remaining - 1);
        }
    }

    private static void sendSettlersDestroyButtons(
            Player acting, Game game, Player target, String planet, int remaining) {
        Planet uH = game.getUnitHolderFromPlanet(planet);
        if (uH == null) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (UnitType type : GROUND_UNITS) {
            int count = uH.getUnitCount(type, target);
            if (count > 0) {
                buttons.add(Buttons.red(
                        acting.factionButtonChecker() + "settlersDestroy_" + remaining + "_" + target.getFaction() + "_"
                                + type.name() + "_" + planet,
                        "Destroy 1 " + type.humanReadableName() + " (" + count + " there)"));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    acting.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no units left to destroy on "
                            + Helper.getPlanetRepresentation(planet, game) + " for _Settlers_.");
            return;
        }
        buttons.add(Buttons.gray("deleteButtons", "Done Destroying"));
        String remainingText = remaining == 1 ? "1 unit" : "up to " + remaining + " units";
        MessageHelper.sendMessageToChannelWithButtons(
                acting.getCorrectChannel(),
                acting.getRepresentationUnfogged() + ", choose " + remainingText + " of "
                        + target.getRepresentationNoPing() + "'s units to destroy on "
                        + Helper.getPlanetRepresentation(planet, game) + " for _Settlers_.",
                buttons);
    }

    private static Set<String> settlersPlanets(Game game, Player target) {
        Set<String> planets = new LinkedHashSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile == null) continue;
            for (Planet uH : tile.getPlanetUnitHolders()) {
                if (!uH.isHomePlanet(game)
                        && ButtonHelper.getPlayersWithUnitsOnAPlanet(game, uH).contains(target)) {
                    planets.add(uH.getName());
                }
            }
        }
        return planets;
    }
}
