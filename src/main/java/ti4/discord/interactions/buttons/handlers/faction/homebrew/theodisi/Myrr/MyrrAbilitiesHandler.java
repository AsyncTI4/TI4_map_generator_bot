package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Myrr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;

@UtilityClass
public class MyrrAbilitiesHandler {
    private static final String FACTORY_LEASE = "factory_lease";
    private static final String FACTORY_LEASE_SYSTEM = "myrrFactoryLeaseSystem_";
    private static final String FACTORY_LEASE_PRODUCE = "myrrFactoryLeaseProduce_";

    public static boolean hasEchoOfTheAnvilDiscount(Player player) {
        if (!player.hasAbility("echo_of_the_anvil")) {
            return false;
        }

        Map<String, Integer> producedByType = new HashMap<>();
        int totalProduced = 0;

        for (Map.Entry<String, Integer> entry : player.getCurrentProducedUnits().entrySet()) {
            String unitType = entry.getKey().split("_", 2)[0];
            int count = entry.getValue();

            totalProduced += count;
            producedByType.merge(unitType, count, Integer::sum);
        }

        return totalProduced >= 2 && producedByType.values().stream().anyMatch(count -> count >= 2);
    }

    public static void offerFactoryLeaseProduction(Game game) {
        if (game == null) {
            return;
        }

        int factoryLeases = (int) game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .flatMap(planet -> planet.getAttachments().stream())
                .filter(attachment -> attachment.contains("factorylease"))
                .count();
        if (factoryLeases < 1) {
            return;
        }

        for (Player player : game.getRealPlayers()) {
            if (!player.hasAbility(FACTORY_LEASE)) {
                continue;
            }

            List<Tile> systems = getFactoryLeaseSystems(game, player);
            if (systems.isEmpty()) {
                continue;
            }

            for (int lease = 1; lease <= factoryLeases; lease++) {
                List<Button> buttons = new ArrayList<>();
                for (Tile tile : systems) {
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + FACTORY_LEASE_SYSTEM + tile.getPosition() + "|" + lease,
                            "Produce in " + tile.getRepresentationForButtons(game, player)));
                }
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCorrectChannel(),
                        player.getRepresentationUnfogged()
                                + ", you may use **Factory Lease** to produce 1 unit other than a **War Sun** in a system containing your space dock. You must pay its cost.",
                        buttons);
            }
        }
    }

    @ButtonHandler(FACTORY_LEASE_SYSTEM)
    public static void chooseFactoryLeaseSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(FACTORY_LEASE_SYSTEM.length()).split("\\|", 2);
        Tile tile = payload.length == 2 ? game.getTileByPosition(payload[0]) : null;
        if (tile == null
                || !player.hasAbility(FACTORY_LEASE)
                || !getFactoryLeaseSystems(game, player).contains(tile)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = Helper.getPlaceUnitButtons(
                event, player, game, tile, FACTORY_LEASE, FACTORY_LEASE_PRODUCE + payload[1]);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", please choose 1 unit other than a **War Sun** to produce using **Factory Lease**.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FACTORY_LEASE_PRODUCE)
    public static void produceWithFactoryLease(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!player.hasAbility(FACTORY_LEASE)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(FACTORY_LEASE_PRODUCE.length());
        int separator = payload.indexOf('_');
        if (separator < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelperModifyUnits.placeUnitAndDeleteButton(
                "placeOneNDone_factorylease_" + payload.substring(separator + 1), event, game, player);
    }

    private static List<Tile> getFactoryLeaseSystems(Game game, Player player) {
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock).stream()
                .filter(tile -> game.getRealPlayersNDummies().stream()
                        .noneMatch(otherPlayer ->
                                otherPlayer != player && FoWHelper.playerHasActualShipsInSystem(otherPlayer, tile)))
                .toList();
    }
}
