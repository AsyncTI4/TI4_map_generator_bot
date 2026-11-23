package ti4.service.megagame;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.DisplayType;
import ti4.image.map.FocusIslandsImage;
import ti4.image.map.MapFocus;
import ti4.image.map.MapRenderPipeline;
import ti4.message.MessageHelper;
import ti4.service.fow.MapSegmentService;

@UtilityClass
public class MegagameShowAsService {

    public static final int DEFAULT_DISTANCE = 1;
    public static final int MAX_DISTANCE = 10;
    private static final String MY_AREA_BUTTON_ID = "megagameMyArea";

    public static boolean isAvailable(Game game) {
        return MegagameTradeOverviewService.isActive(game) || MegagameGmPanelService.isAvailable(game);
    }

    public static Optional<Button> myAreaButton(Game game) {
        if (game == null || !isAvailable(game)) {
            return Optional.empty();
        }
        return Optional.of(Buttons.blue(MY_AREA_BUTTON_ID, "My Area"));
    }

    @ButtonHandler(value = MY_AREA_BUTTON_ID, save = false)
    public static void showMyArea(ButtonInteractionEvent event, Game game) {
        Player viewer = game.getPlayer(MapSegmentService.viewerId(game, event));
        if (viewer == null || !viewer.isRealPlayer()) {
            reply(event, "Only players have an area. Use `/show_game show_as` to view a faction.");
            return;
        }
        show(event, game, viewer, DEFAULT_DISTANCE, false, DisplayType.all);
    }

    public static void show(
            GenericInteractionCreateEvent event,
            Game game,
            String factionOrColor,
            int distance,
            boolean wholeMap,
            DisplayType displayType) {
        if (!isAvailable(game)) {
            reply(event, "`show_as` only works in megagames outside Fog of War.");
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(AliasHandler.resolveColor(factionOrColor.toLowerCase()));
        if (target == null || !target.isRealPlayer()) {
            reply(event, "Could not find that faction or colour in this game.");
            return;
        }
        show(event, game, target, distance, wholeMap, displayType);
    }

    private static void show(
            GenericInteractionCreateEvent event,
            Game game,
            Player target,
            int distance,
            boolean wholeMap,
            DisplayType displayType) {
        if (!isAvailable(game)) {
            reply(event, "`show_as` only works in megagames outside Fog of War.");
            return;
        }
        Set<String> presence = MegagamePlayerReach.presence(game, target);
        if (presence.isEmpty() && displayType != DisplayType.stats) {
            reply(event, target.getRepresentationUnfoggedNoPing() + " has no systems on the map.");
            return;
        }
        Set<String> lit = MegagamePlayerReach.withinDistance(game, target, presence, distance);
        reply(
                event,
                "Showing the game as " + target.getRepresentationUnfoggedNoPing() + " with distance " + distance + ": "
                        + presence.size() + " systems with presence, " + lit.size() + " shown.");
        if (wholeMap) {
            MapRenderPipeline.queueFocused(
                    game,
                    displayType,
                    new MapFocus(target.getFaction(), lit),
                    fileUpload -> MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event));
            return;
        }
        String faction = target.getFaction();
        MapRenderPipeline.queueImages(
                game,
                "Megagame show as " + faction,
                snapshot -> images(snapshot, faction, distance, displayType),
                fileUploads -> MessageHelper.sendEphemeralFilesInResponse(fileUploads, event));
    }

    private static List<FileUpload> images(Game snapshot, String faction, int distance, DisplayType displayType) {
        Player target = snapshot.getPlayerFromColorOrFaction(faction);
        if (target == null) {
            return List.of();
        }
        Set<String> lit = MegagamePlayerReach.withinDistance(
                snapshot, target, MegagamePlayerReach.presence(snapshot, target), distance);
        List<FileUpload> fileUploads = new ArrayList<>();
        if (displayType != DisplayType.stats && !lit.isEmpty()) {
            fileUploads.add(FocusIslandsImage.upload(snapshot, target, lit, distance));
        }
        if (displayType != DisplayType.map) {
            fileUploads.add(
                    MapRenderPipeline.renderOnThisThread(snapshot, DisplayType.stats, new MapFocus(faction, lit)));
        }
        return fileUploads;
    }

    private static void reply(GenericInteractionCreateEvent event, String message) {
        if (event instanceof IReplyCallback callback) {
            MegagameGmPanelService.sendEphemeral(callback.getHook(), message, null);
        }
    }
}
