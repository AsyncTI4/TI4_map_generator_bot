package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.brynfolk;

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
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class BrynfolkUnitHandler {
    private static final String DEPLOY_TITHE_EATER = "deployBrynfolkMech_";

    public static void addTitheEaterDeployButton(
            List<Button> buttons, Game game, Player player, Tile tile, String planet) {
        if (!player.hasUnit("brynfolk_mech")) {
            return;
        }
        buttons.add(Buttons.green(
                player.factionButtonChecker() + DEPLOY_TITHE_EATER + planet,
                "Deploy Dreyki Tithe-Eater",
                FactionEmojis.brynfolk));
    }

    @ButtonHandler(DEPLOY_TITHE_EATER)
    public static void deployTitheEater(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.substring(DEPLOY_TITHE_EATER.length());
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null
                || !player.hasUnit("brynfolk_mech")
                || !BrynfolkAbilityHandler.returnOwnBlotToken(player, game)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + planet);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " returned 1 control token from Blot and placed a Dreyki Tithe-Eater on "
                        + Helper.getPlanetRepresentation(planet, game) + ".");
        ButtonHelper.deleteMessage(event);
    }
}
