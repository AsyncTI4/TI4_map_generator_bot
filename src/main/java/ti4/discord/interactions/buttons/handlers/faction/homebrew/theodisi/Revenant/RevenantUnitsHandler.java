package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class RevenantUnitsHandler {
    private static final String GRAVE_ROBBERS = "revenant_mech";
    private static final String PLACE_GRAVE_ROBBERS = "placeRevenantMech_";
    private static final String PAGE_GRAVE_ROBBERS = "pageRevenantMech_";

    public static void doRevenantMechCheck(Game game, Player player) {
        if (!player.hasUnit(GRAVE_ROBBERS)) {
            return;
        }

        List<Button> buttons = getRevenantMechPlacementButtons(game, player);
        if (buttons.isEmpty()) {
            return;
        }
        String message = player.getRepresentation()
                + ", you may use the DEPLOY ability of Grave Robbers (Revenant mech) to place 1 mech from your reinforcements on a planet you control. Please choose the planet on which you wish to place it.";
        String prefix = player.factionButtonChecker() + PAGE_GRAVE_ROBBERS;
        List<Button> extraButtons = List.of(Buttons.red("deleteButtons", "Decline"));
        List<Button> displayedButtons = new ArrayList<>(buttons);
        displayedButtons.addAll(extraButtons);
        if (displayedButtons.size() > 25) {
            displayedButtons = NewStuffHelper.buttonPagination(buttons, extraButtons, prefix, 25, 0, false);
        }

        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, displayedButtons);
    }

    @ButtonHandler(PAGE_GRAVE_ROBBERS)
    public static void pageRevenantMechPlacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasUnit(GRAVE_ROBBERS)) {
            return;
        }
        List<Button> buttons = getRevenantMechPlacementButtons(game, player);
        String message = player.getRepresentation()
                + ", you may use the DEPLOY ability of Grave Robbers (Revenant mech) to place 1 mech from your reinforcements on a planet you control. Please choose the planet on which you wish to place it.";
        String prefix = player.factionButtonChecker() + PAGE_GRAVE_ROBBERS;
        NewStuffHelper.checkAndHandlePaginationChange(
                event,
                player.getCorrectChannel(),
                buttons,
                List.of(Buttons.red("deleteButtons", "Decline")),
                message,
                prefix,
                buttonID);
    }

    @ButtonHandler(PLACE_GRAVE_ROBBERS)
    public static void placeRevenantMech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.substring(PLACE_GRAVE_ROBBERS.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        if (!player.hasUnit(GRAVE_ROBBERS)
                || planet == null
                || !player.getPlanets().contains(planetName)
                || planet.isSpaceStation(game)
                || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
            return;
        }

        AddUnitService.addUnits(
                event, game.getTileFromPlanet(planetName), game, player.getColor(), "1 mech " + planetName);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " placed 1 Grave Robber (Revenant mech) on "
                        + Helper.getPlanetRepresentation(planetName, game) + ".");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getRevenantMechPlacementButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null
                    || planet.isSpaceStation(game)
                    || planet.getTokenList().stream().anyMatch(token -> token.contains("dmz"))) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_GRAVE_ROBBERS + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        return buttons;
    }
}
