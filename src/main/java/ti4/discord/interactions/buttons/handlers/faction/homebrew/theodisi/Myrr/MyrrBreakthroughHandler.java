package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Myrr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

@UtilityClass
public class MyrrBreakthroughHandler {

    private static final String BT = "myrrbt";
    public static final String REMOTE_WORKFORCE_KEY = "myrrBtRemoteWorkforce";
    public static final String PRODUCTION_USED_KEY = "myrrBtProductionUsed";

    public static boolean usedUnitProduction(String productionContext) {
        return switch (productionContext) {
            case "tacticalAction",
                    "warfare",
                    "construction",
                    "ministerBuild",
                    "anarchy7Build",
                    "lumi7Build",
                    "obsessivedesigns",
                    "celdauriHero" -> true;
            default -> false;
        };
    }

    public static void offerRemoteWorkforce(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasUnlockedBreakthrough(BT)
                || player.isBreakthroughExhausted(BT)
                || player.getCurrentProducedUnits().isEmpty()) {
            return;
        }

        Set<String> producedInSystems = new HashSet<>();
        for (String producedUnit : player.getCurrentProducedUnits().keySet()) {
            producedInSystems.add(producedUnit.split("_")[1]);
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (producedInSystems.contains(tile.getPosition())
                    || Helper.getProductionValue(player, game, tile, false) < 1) {
                continue;
            }

            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "myrrBtProduce_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }

        if (buttons.isEmpty()) {
            return;
        }

        buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", you may exhaust _Remote Workforce_ to resolve PRODUCTION in another system.",
                buttons);
    }

    @ButtonHandler("myrrBtProduce_")
    public static void resolveRemoteWorkforce(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String position = buttonID.replace("myrrBtProduce_", "");
        Tile tile = game.getTileByPosition(position);

        Set<String> producedInSystems = new HashSet<>();
        for (String producedUnit : player.getCurrentProducedUnits().keySet()) {
            producedInSystems.add(producedUnit.split("_")[1]);
        }

        if (!player.hasUnlockedBreakthrough(BT)
                || player.isBreakthroughExhausted(BT)
                || tile == null
                || producedInSystems.contains(position)
                || Helper.getProductionValue(player, game, tile, false) < 1) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", that system is no longer eligible for _Remote Workforce_.");
            return;
        }

        player.setBreakthroughExhausted(BT, true);
        game.setStoredValue(REMOTE_WORKFORCE_KEY + player.getFaction(), position);
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, tile, "myrrBt", "place");
        int production = Helper.getProductionValue(player, game, tile, false);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + " is resolving _Remote Workforce_ in "
                        + tile.getRepresentationForButtons(game, player)
                        + ". Its total PRODUCTION is "
                        + production
                        + " (+2 from _Remote Workforce_) and its total cost is reduced by 1.",
                buttons);
    }
}
