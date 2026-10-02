package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.lunarium;

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
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class LunariumUnitHandler {

    public static void offerDeployMechButton(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        String factionCheckerPrefix = player.factionButtonChecker();
        for (String planet : player.getPlanets()) {
            buttons.add(Buttons.gray(
                    factionCheckerPrefix + "lunariumDeployMech_" + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        if (buttons.isEmpty()) return;
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        String msg = player.getRepresentation()
                + ", you may place 1 mech on a planet you control using Lunar Mercenary's DEPLOY ability.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
    }

    @ButtonHandler("lunariumDeployMech_")
    public static void resolveDeployMech(Player player, String buttonID, Game game, ButtonInteractionEvent event) {
        String planet = buttonID.replace("lunariumDeployMech_", "");
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " placed 1 mech on " + Helper.getPlanetRepresentation(planet, game)
                        + " using Lunar Mercenary's DEPLOY ability.");
        ButtonHelper.deleteMessage(event);
    }
}
