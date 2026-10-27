package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.morpha;

import java.util.ArrayList;
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
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class MorphaAbilityHandler {
    @ButtonHandler("useHydroExtract_")
    public static void offerHydrosphericExtractionUnit(
            ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String planetName = buttonID.replace("useHydroExtract_", "");
        Planet planet = game.getUnitHolderFromPlanet(planetName);

        if (planet == null
                || isSetup(game)
                || !player.hasAbility("hydrospheric_extraction")
                || "used".equals(game.getStoredValue("hydroExtractUsed_" + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (UnitKey unitKey : planet.getUnitKeys()) {
            if (!player.unitBelongsToPlayer(unitKey) || planet.getUnitCount(unitKey) < 1) {
                continue;
            }

            buttons.add(Buttons.red(
                    player.factionButtonChecker() + "hydroExtractDestroy_" + planetName + "|" + unitKey.asyncID(),
                    "Destroy 1 " + unitKey.unitType().humanReadableName(),
                    unitKey.unitEmoji()));
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing()
                            + ", you have no units on "
                            + planet.getRepresentation(game)
                            + " to destroy for _Hydrospheric Extraction_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose 1 of your units on "
                        + planet.getRepresentation(game)
                        + " to destroy for _Hydrospheric Extraction_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("hydroExtractDestroy_")
    public static void resolveHydrosphericExtraction(
            ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String[] parts = buttonID.replace("hydroExtractDestroy_", "").split("\\|");
        if (parts.length != 2
                || isSetup(game)
                || !player.hasAbility("hydrospheric_extraction")
                || "used".equals(game.getStoredValue("hydroExtractUsed_" + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String planetName = parts[0];
        UnitType unitType = Units.findUnitType(parts[1]);
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);

        if (unitType == null || planet == null || tile == null || planet.getUnitCount(unitType, player) < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        RemoveUnitService.removeUnit(event, tile, game, player, planet, unitType, 1, true);
        game.setStoredValue("hydroExtractUsed_" + player.getFaction(), "used");
        ActionCardHelper.drawActionCards(player, 1);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " destroyed 1 "
                        + unitType.humanReadableName()
                        + " on "
                        + planet.getRepresentation(game)
                        + " for _Hydrospheric Extraction_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void clearHydrosphericExtraction(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue("hydroExtractUsed_" + player.getFaction());
        }
    }

    private static boolean isSetup(Game game) {
        return game.getRevealedPublicObjectives().size() < 3
                || "setup".equalsIgnoreCase(game.getPhaseOfGame())
                || "playerSetup".equalsIgnoreCase(game.getPhaseOfGame());
    }
}
