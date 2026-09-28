package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Xytheris;

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
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class XytherisUnitHandler {
    private static final String HEXAN_PLANET = "chooseHexanPlanetTarget_";

    public static void offerHexanButtons(Game game, Player player, Tile tile) {
        if (game == null || player == null || tile == null || !player.hasUnit("xytheris_mech")) {
            return;
        }

        List<Button> planets = new ArrayList<>();
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (ButtonHelper.getNumberOfInfantryOnPlanet(planet.getName(), game, player) > 0) {
                planets.add(Buttons.green(
                        player.factionButtonChecker() + HEXAN_PLANET + planet.getName(),
                        planet.getRepresentation(game),
                        PlanetEmojis.getPlanetEmoji(planet.getName())));
            }
        }
        planets.add(Buttons.red("deleteButtons", "Decline"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", someone activated a system containing a planet you control and as such you may spend 1 trade good to replace an infantry on one of those planets with 1 mech from your reinforcements.",
                planets);
    }

    @ButtonHandler(HEXAN_PLANET)
    public static void resolveHexanStingers(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasUnit("xytheris_mech")) {
            return;
        }

        String planetName = buttonID.replace(HEXAN_PLANET, "");
        if (planetName == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find planet.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Tile tile = game.getTileFromPlanet(planetName);
        UnitHolder uH = game.getUnitHolderFromPlanet(planetName);

        RemoveUnitService.removeUnit(event, tile, game, player, uH, UnitType.Infantry, 1, null);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planetName);
        player.setTg(player.getTg() - 1);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " replaced an infantry on "
                        + Helper.getPlanetRepresentation(planetName, game)
                        + " with 1 mech from your reinforcements. A trade good has already been deducted and you now have "
                        + player.getTg() + " trade goods.");
    }
}
