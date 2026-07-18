package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
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

@UtilityClass
public class VerydithBreakthroughHandler {

    public static void offerUnyieldingAccord(GenericInteractionCreateEvent event, Player player, Tile tile) {
        if (player == null || tile == null || !player.hasUnlockedBreakthrough("verydithbt")) {
            return;
        }

        List<Button> buttons = new ArrayList<>();

        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (!player.hasPlanet(planet.getName()) || player.hasPlanetReady(planet.getName())) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "verydithBt_" + planet.getName(),
                    "Ready and explore " + planet.getName()));
        }

        if (!buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", someone else's command token was placed in a system with your planets. Use these buttons to ready 1 of those and explore it using _Unyielding Accord_:",
                    buttons);
        }
    }

    @ButtonHandler("verydithBt_")
    public static void resolveUnyieldingAccord(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null) {
            return;
        }

        String planetName = buttonID.replace("verydithBt_", "");
        Planet planet = game.getPlanetsInfo().get(planetName);

        player.refreshPlanet(planetName);

        List<Button> exploreButtons = ButtonHelper.getPlanetExplorationButtons(game, planet, player);
        String message = player.getRepresentation() + " readied " + Helper.getPlanetRepresentation(planetName, game)
                + " due to _Unyielding Accord_.";
        if (exploreButtons == null || exploreButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getMessageChannel(), message + " You may now explore it.", exploreButtons);
        }

        ButtonHelper.deleteMessage(event);
    }
}
