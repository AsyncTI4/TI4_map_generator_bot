package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.vaettir;

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
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class VaettirUnitHandler {
    private static final String DEPLOY_GROVEWARDEN = "deployGrovewarden_";

    public static int getMimisBoughCombatBonus(Player player, Tile tile) {
        if (!ButtonHelper.doesPlayerHaveFSHere("vaettir_flagship", player, tile)) {
            return 0;
        }
        return (int) tile.getPlanetUnitHolders().stream()
                .flatMap(planet -> planet.getTokenList().stream())
                .filter(token -> token.startsWith("attachment_root_"))
                .count();
    }

    public static void offerGrovewardenDeploy(Game game, Player player, Tile tile) {
        if (!player.hasUnit("vaettir_mech")
                || ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) >= 4) {
            return;
        }
        List<Button> buttons = new ArrayList<>(tile.getPlanetUnitHolders().stream()
                .flatMap(planet -> planet.getTokenList().stream()
                        .filter(token -> token.startsWith("attachment_root_"))
                        .map(token -> deployButton(player, planet, token)))
                .toList());
        if (buttons.isEmpty()) {
            return;
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may use **Grovewarden** to replace a Root token in the active system with 1 mech.",
                buttons);
    }

    @ButtonHandler(DEPLOY_GROVEWARDEN)
    public static void deployGrovewarden(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(DEPLOY_GROVEWARDEN.length()).split("\\|", 2);
        if (payload.length != 2 || !player.hasUnit("vaettir_mech")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        Planet planet = activeSystem == null ? null : activeSystem.getUnitHolderFromPlanet(payload[0]);
        if (planet == null || !planet.removeToken(payload[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AddUnitService.addUnits(event, activeSystem, game, player.getColor(), "1 mech " + planet.getName());
        VaettirAbilityHandler.syncOldGrowthRootCards(game);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " replaced a Root token on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + " with a Grovewarden.");
        ButtonHelper.deleteMessage(event);
    }

    private static Button deployButton(Player player, Planet planet, String rootToken) {
        return Buttons.green(
                player.factionButtonChecker() + DEPLOY_GROVEWARDEN + planet.getName() + "|" + rootToken,
                "Replace Root on " + planet.getName() + " with Mech",
                FactionEmojis.vaettir);
    }
}
