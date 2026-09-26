package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionTileHelper;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperExplore;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.TileModel;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class AncientMapsLLButtonHandler {
    private static final String RESOLVE_ANCIENT_MAPS = "resolveAncientMapsAC";
    private static final String PURGE_ANCIENT_MAPS_FRAGMENT = "purgeAncientMapsFragment_";
    private static final String DONE_ANCIENT_MAPS_FRAGMENTS = "doneAncientMapsFragments";
    private static final String ANCIENT_MAPS_FRAGMENTS = "ancientMapsFragments_";
    private static final String ANCIENT_MAPS_TILE = "ancientMapsTile_";
    private static final String PLACE_ANCIENT_MAPS = "placeAncientMaps_";

    @ButtonHandler(RESOLVE_ANCIENT_MAPS)
    public static void resolveAncientMaps(ButtonInteractionEvent event, Game game, Player player) {
        if (player.getFragments().size() < 2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " cannot resolve _Ancient Maps_ because they have fewer than 2 relic fragments.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(ANCIENT_MAPS_FRAGMENTS + player.getFaction());

        List<Button> buttons = new ArrayList<>();
        for (String fragmentId : player.getFragments()) {
            ExploreModel fragment = Mapper.getExplore(fragmentId);
            if (fragment != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + PURGE_ANCIENT_MAPS_FRAGMENT + fragmentId,
                        "Purge " + fragment.getName(),
                        ExploreEmojis.getFragEmoji(fragment.getType())));
            }
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + DONE_ANCIENT_MAPS_FRAGMENTS, "Done Purging Fragments"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + ", please choose 2 relic fragments to purge for _Ancient Maps_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PURGE_ANCIENT_MAPS_FRAGMENT)
    public static void purgeAncientMapsFragment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String fragmentId = buttonID.substring(PURGE_ANCIENT_MAPS_FRAGMENT.length());
        String key = ANCIENT_MAPS_FRAGMENTS + player.getFaction();
        String selected = game.getStoredValue(key);
        List<String> selectedFragments = selected.isEmpty() ? List.of() : List.of(selected.split("\\|"));

        if (selectedFragments.size() >= 2
                || selectedFragments.contains(fragmentId)
                || !player.getFragments().contains(fragmentId)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removeFragment(fragmentId);
        game.purgeExplore(fragmentId);
        game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
        if (fragmentId.startsWith("supermassive")) {
            ButtonHelperExplore.offerSupermassiveFragmentGainIfApplicable(game, player, event, fragmentId);
        }
        game.setStoredValue(key, selected.isEmpty() ? fragmentId : selected + "|" + fragmentId);

        ExploreModel fragment = Mapper.getExplore(fragmentId);
        if (fragment != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " purged "
                            + ExploreEmojis.getFragEmoji(fragment.getType())
                            + " _"
                            + fragment.getName()
                            + "_.");
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(DONE_ANCIENT_MAPS_FRAGMENTS)
    public static void finishAncientMapsFragmentPurge(ButtonInteractionEvent event, Game game, Player player) {
        String fragments = game.getStoredValue(ANCIENT_MAPS_FRAGMENTS + player.getFaction());
        if (fragments.isEmpty() || fragments.split("\\|").length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You must purge exactly 2 relic fragments first.");
            return;
        }

        List<String> drawnTiles = OblivionTileHelper.drawUnusedTiles(game, null, 1);
        if (drawnTiles.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " purged 2 relic fragments, but there are no unused blue-backed tiles to draw.");
            game.removeStoredValue(ANCIENT_MAPS_FRAGMENTS + player.getFaction());
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tileId = drawnTiles.getFirst();
        List<Button> placementButtons = OblivionTileHelper.getPlacementButtons(
                game, player, tileId, player.factionButtonChecker() + PLACE_ANCIENT_MAPS);

        if (placementButtons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " drew "
                            + TileHelper.getTileById(tileId).getName()
                            + " for _Ancient Maps_, but there is no legal edge position.");
            game.removeStoredValue(ANCIENT_MAPS_FRAGMENTS + player.getFaction());
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(ANCIENT_MAPS_TILE + player.getFaction(), tileId);
        game.removeStoredValue(ANCIENT_MAPS_FRAGMENTS + player.getFaction());

        String message = player.getRepresentationNoPing()
                + ", please choose an edge position for "
                + TileHelper.getTileById(tileId).getName()
                + " with _Ancient Maps_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_ANCIENT_MAPS + tileId + "_";

        TileModel tileModel = TileHelper.getTileById(tileId);
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                event.getMessageChannel(),
                message,
                tileModel == null ? List.of() : List.of(tileModel.getRepresentationEmbed(false)),
                NewStuffHelper.buttonPagination(placementButtons, buttonPrefix, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_ANCIENT_MAPS)
    public static void placeAncientMapsTile(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String placementData = buttonID.substring(PLACE_ANCIENT_MAPS.length());
        int tileIdEnd = placementData.lastIndexOf('_');
        if (tileIdEnd <= 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String tileId = placementData.substring(0, tileIdEnd);
        String position = placementData.substring(tileIdEnd + 1);
        if (!tileId.equals(game.getStoredValue(ANCIENT_MAPS_TILE + player.getFaction()))) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> placementButtons = OblivionTileHelper.getPlacementButtons(
                game, player, tileId, player.factionButtonChecker() + PLACE_ANCIENT_MAPS);
        String message = player.getRepresentationNoPing()
                + ", please choose an edge position for "
                + TileHelper.getTileById(tileId).getName()
                + " with _Ancient Maps_.";
        String buttonPrefix = player.factionButtonChecker() + PLACE_ANCIENT_MAPS + tileId + "_";

        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), placementButtons, message, buttonPrefix, buttonID)) {
            return;
        }

        Tile placedTile = OblivionTileHelper.placeTile(game, tileId, position);
        if (placedTile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", that edge position is no longer legal for _Ancient Maps_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.removeStoredValue(ANCIENT_MAPS_TILE + player.getFaction());

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + " placed "
                        + placedTile.getRepresentationForButtons(game, player)
                        + " with _Ancient Maps_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void clearAncientMaps(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(ANCIENT_MAPS_FRAGMENTS + player.getFaction());
            game.removeStoredValue(ANCIENT_MAPS_TILE + player.getFaction());
        }
    }
}
