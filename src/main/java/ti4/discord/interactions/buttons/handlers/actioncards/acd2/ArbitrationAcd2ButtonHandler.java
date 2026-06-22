package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
class ArbitrationAcd2ButtonHandler {

    @ButtonHandler("resolveArbitration")
    public static void resolveArbitration(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = getArbitrationOwnerButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible planets for _Arbitration_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose which player's planets to inspect for _Arbitration_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arbitrationOwner_")
    public static void resolveArbitrationOwner(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String owner = buttonID.replace("arbitrationOwner_", "");
        List<Button> buttons = getArbitrationPlanetButtons(game, player, owner);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible planets for _Arbitration_ in that category.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose a non-home planet with ground forces for _Arbitration_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arbitrationPlanet_")
    public static void resolveArbitrationPlanet(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] buttonParts = buttonID.split("_", 3);
        if (buttonParts.length < 3) {
            return;
        }
        String planet = buttonParts[2];
        List<Button> buttons = getArbitrationTargetButtons(game, player, planet);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " has no eligible target players for _Arbitration_ on "
                            + Helper.getPlanetRepresentation(planet, game) + ".");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose who will place 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planet, game) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arbitrationTarget_")
    public static void resolveArbitrationTarget(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String arbitrationTarget = buttonID.replace("arbitrationTarget_", "");
        int lastSeparator = arbitrationTarget.lastIndexOf('_');
        if (lastSeparator < 0) {
            return;
        }

        String planet = arbitrationTarget.substring(0, lastSeparator);
        String faction = arbitrationTarget.substring(lastSeparator + 1);
        Player target = game.getPlayerFromColorOrFaction(faction);
        Tile tile = game.getTileFromPlanet(planet);
        if (target == null || tile == null || target == player || target.hasPlanet(planet)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Arbitration_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, target.getColor(), "inf " + planet);
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);

        String planetRepresentation = Helper.getPlanetRepresentation(planet, game);
        String message = player.getRepresentation() + " chose " + target.getRepresentationNoPing()
                + " to place 1 infantry into coexistence on " + planetRepresentation + " using _Arbitration_.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        if (!Objects.equals(player.getCorrectChannel(), target.getCorrectChannel())) {
            MessageHelper.sendMessageToChannel(target.getCorrectChannel(), message);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getArbitrationOwnerButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player owner : game.getRealPlayers()) {
            if (owner == player
                    || getArbitrationPlanetButtons(game, player, owner.getFaction())
                            .isEmpty()) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "arbitrationOwner_" + owner.getFaction(), owner.getColor()));
            } else {
                Button button = Buttons.gray(
                        player.factionButtonChecker() + "arbitrationOwner_" + owner.getFaction(),
                        owner.getFactionModel().getShortName());
                buttons.add(button.withEmoji(Emoji.fromFormatted(owner.getFactionEmoji())));
            }
        }

        if (!getArbitrationPlanetButtons(game, player, "neutralUnits").isEmpty()) {
            buttons.add(Buttons.gray(player.factionButtonChecker() + "arbitrationOwner_neutralUnits", "Neutral Units"));
        }
        return buttons;
    }

    private static List<Button> getArbitrationPlanetButtons(Game game, Player player, String owner) {
        List<String> planets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isHomeSystem(game)) {
                continue;
            }
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!isArbitrationPlanetInCategory(game, planet, owner)
                        || getArbitrationTargetButtons(game, player, planet.getName())
                                .isEmpty()) {
                    continue;
                }
                planets.add(planet.getName());
            }
        }

        Collections.sort(planets);
        return planets.stream()
                .map(planet -> Buttons.green(
                        player.factionButtonChecker() + "arbitrationPlanet_" + owner + "_" + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList();
    }

    private static boolean isArbitrationPlanetInCategory(Game game, Planet planet, String owner) {
        if ("neutralUnits".equals(owner)) {
            return planet.hasGroundForces(game.getNeutral());
        }
        Player planetOwner = game.getPlanetOwner(planet.getName());
        return planetOwner != null && owner.equals(planetOwner.getFaction()) && planet.hasGroundForces(game);
    }

    private static List<Button> getArbitrationTargetButtons(Game game, Player player, String planet) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target == player || target.hasPlanet(planet)) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "arbitrationTarget_" + planet + "_" + target.getFaction(),
                        target.getColor()));
            } else {
                Button button = Buttons.gray(
                        player.factionButtonChecker() + "arbitrationTarget_" + planet + "_" + target.getFaction(),
                        target.getFactionModel().getShortName());
                String factionEmojiString = target.getFactionEmoji();
                buttons.add(button.withEmoji(Emoji.fromFormatted(factionEmojiString)));
            }
        }
        return buttons;
    }
}
