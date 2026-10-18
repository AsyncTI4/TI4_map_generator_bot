package ti4.discord.interactions.buttons.handlers.faction.homebrew.bluereverie;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class PharadnBreakthroughHandler {

    @ButtonHandler("hiddenVaultsProduce_")
    public static void resolveHiddenVaultsPlacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnlockedBreakthrough("pharadnbt")) {
            return;
        }
        String exploredPlanet = buttonID.substring("hiddenVaultsProduce_".length());

        PlanetModel planet = Mapper.getPlanet(exploredPlanet);
        if (planet == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        RemoveUnitService.removeUnits(event, player.getNomboxTile(), game, player.getColor(), "1 inf");
        AddUnitService.addUnits(
                event, game.getTileFromPlanet(exploredPlanet), game, player.getColor(), "1 inf " + exploredPlanet);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " produced 1 infantry on " + Helper.getPlanetRepresentation(planet.getID(), game)
                        + " using _Hidden Vaults_. 1 infantry has already been removed from your captured units.");

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("hiddenVaultsDestroy")
    public static void resolveHiddenVaultsDestroy(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasReadyBreakthrough("pharadnbt")) {
            return;
        }

        player.setBreakthroughExhausted("pharadnbt", true);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " is using _Hidden Vaults_ to destroy any number of their units on the game board.",
                ButtonHelper.getTilesWithPredicateForAction(
                        player, game, "hiddenVaultsChooseDestroy", tile -> tile.containsPlayersUnits(player), true));

        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("hiddenVaultsChooseDestroy_")
    public static void chooseHiddenVaultsDestroyTile(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("hiddenVaultsChooseDestroy_", "");
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !tile.containsPlayersUnits(player)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Button destroyUnits = Buttons.red(
                player.factionButtonChecker() + "getDamageButtons_" + position + "_remove",
                "Destroy Units in " + tile.getRepresentationForButtons(game, player));
        Button selectAnotherSystem = Buttons.gray(
                player.factionButtonChecker() + "hiddenVaultsSelectAnotherSystem", "Select Another System");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose units to destroy with _Hidden Vaults_.",
                List.of(destroyUnits, selectAnotherSystem));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("hiddenVaultsSelectAnotherSystem")
    public static void selectAnotherHiddenVaultsSystem(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose another system for _Hidden Vaults_.",
                ButtonHelper.getTilesWithPredicateForAction(
                        player, game, "hiddenVaultsChooseDestroy", tile -> tile.containsPlayersUnits(player), true));
        ButtonHelper.deleteMessage(event);
    }
}
