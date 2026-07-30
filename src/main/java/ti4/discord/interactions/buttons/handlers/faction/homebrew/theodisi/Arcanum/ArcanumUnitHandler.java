package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class ArcanumUnitHandler {
    private static final String RUNEBOUND = "arcanum_mech";
    private static final String PLACE_INF_WITH_MECH = "placeInfWithRuneboundMech_";

    public static void getRuneboundButtons(Player player, Game game, String techID) {
        if (player == null
                || game == null
                || techID == null
                || !player.ownsUnit(RUNEBOUND)
                || ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)
                        .isEmpty()) {
            return;
        }
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)) {
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (planet.getUnitCount(UnitType.Mech, player.getColor()) <= 0) {
                    continue;
                }
                String planetName = planet.getName();

                buttons.add(Buttons.green(
                        player.factionButtonChecker() + PLACE_INF_WITH_MECH + planetName + "|" + techID,
                        Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red("deleteButtons", "Decline"));

        TechnologyModel tech = Mapper.getTech(techID);
        int prereqs = tech == null || tech.getRequirements().isEmpty()
                ? 0
                : tech.getRequirements().get().length() + 1;

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", you may place " + prereqs
                        + " infantry on a planet that contains a Rune-Bound Sentinel (Arcanum mech).",
                buttons);
    }

    @ButtonHandler(PLACE_INF_WITH_MECH)
    public static void resolveRuneboundInfPlacement(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !player.ownsUnit(RUNEBOUND)
                || ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Mech)
                        .isEmpty()) {
            return;
        }

        String payload = buttonID.substring(PLACE_INF_WITH_MECH.length());
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            return;
        }

        String planetName = parts[0];
        String techID = parts[1];

        TechnologyModel tech = Mapper.getTech(techID);

        if (planetName == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find that planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (tech == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not resolve the technology.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileFromPlanet(planetName);
        Planet planet = tile == null
                ? null
                : tile.getPlanetUnitHolders().stream()
                        .filter(holder -> planetName.equals(holder.getName()))
                        .findFirst()
                        .orElse(null);
        if (planet == null || planet.getUnitCount(UnitType.Mech, player.getColor()) <= 0) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That planet no longer contains one of your mechs.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int prereqs = tech == null || tech.getRequirements().isEmpty()
                ? 0
                : tech.getRequirements().get().length() + 1;

        AddUnitService.addUnits(event, tile, game, player.getColor(), prereqs + " inf " + planetName);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed " + prereqs + " infantry on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " using a Rune-Bound Sentinel (Arcanum mech).");
    }
}
