package ti4.discord.interactions.buttons.handlers.game;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.image.MapRenderPipeline;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.fow.MapOverviewService;
import ti4.service.fow.MapSegmentService;
import ti4.settings.users.RefreshMapStyle;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
class ShowGameButtonHandler {

    private static final String REFRESH = "showGameAgain";
    private static final String SHOW_MAP = "showMap";
    private static final String SHOW_PLAYER_AREAS = "showPlayerAreas";
    private static final String SHOW_FULL_MAP = "showFullMap";

    @ButtonHandler(value = REFRESH, save = false)
    public static void simpleShowGame(Game game, ButtonInteractionEvent event, String buttonID) {
        if (!mayRenderHere(game, event)) return;
        String segment = MapSegmentService.segmentFrom(buttonID, REFRESH);
        if (refreshMapStyle(event).isSplit()) {
            offerMapParts(game, event, segment);
            return;
        }
        ShowGameService.simpleShowGame(game, event, DisplayType.all, segment);
    }

    private static void offerMapParts(Game game, ButtonInteractionEvent event, @Nullable String segment) {
        String message = "Which part of the map do you want to see?";
        List<Button> buttons = List.of(
                Buttons.gray(MapSegmentService.withSegment(SHOW_MAP, segment), "Show Map"),
                Buttons.gray(SHOW_PLAYER_AREAS, "Show Player Stats"),
                Buttons.gray(MapSegmentService.withSegment(SHOW_FULL_MAP, segment), "Show Full Map"));
        if (postsInChannel(event)) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
            if (REFRESH.equals(event.getComponentId())) {
                event.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
            }
        } else {
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, message, buttons);
        }
    }

    private static boolean mayRenderHere(@Nullable Game game, ButtonInteractionEvent event) {
        if (game == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Could not find a game for this channel.");
            return false;
        }
        if (MapSegmentService.isFoggedView(game, event) || FoWHelper.canSeeWholeMap(game, event)) {
            return true;
        }
        MessageHelper.sendEphemeralMessageToEventChannel(
                event, "In a Fog of War game the map can only be shown in your private channel.");
        return false;
    }

    private static boolean postsInChannel(ButtonInteractionEvent event) {
        return refreshMapStyle(event).postsInChannel();
    }

    private static RefreshMapStyle refreshMapStyle(ButtonInteractionEvent event) {
        return UserSettingsManager.get(event.getUser().getId()).getRefreshMapStyle();
    }

    private static void showMapPart(
            Game game, ButtonInteractionEvent event, DisplayType part, @Nullable String segment) {
        if (!mayRenderHere(game, event)) return;
        boolean inChannel = postsInChannel(event);
        MapRenderPipeline.queue(game, event, part, segment, fileUpload -> {
            if (!inChannel) {
                MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event);
                return;
            }
            List<Button> mapButtons = new ArrayList<>();
            if (part != DisplayType.stats) {
                mapButtons.addAll(MapSegmentService.switchButtons(
                        game, MapSegmentService.viewerId(game, event), MapSegmentService.isFoggedView(game, event)));
                MapOverviewService.overviewButton(game, event).ifPresent(mapButtons::add);
            }
            if (mapButtons.isEmpty()) {
                MessageHelper.sendFileUploadToChannel(event.getMessageChannel(), fileUpload);
            } else {
                ButtonHelper.sendFileWithCorrectButtons(
                        event.getMessageChannel(), fileUpload, null, mapButtons, game, null);
            }
        });
    }

    @ButtonHandler(value = "showGameEphemeral", save = false)
    public static void simpleEphemeralShowGame(Game game, ButtonInteractionEvent event) {
        ShowGameService.simpleEphemeralShowGame(game, event);
    }

    @ButtonHandler(value = SHOW_MAP, save = false)
    public static void showMap(Game game, ButtonInteractionEvent event, String buttonID) {
        showMapPart(game, event, DisplayType.map, MapSegmentService.segmentFrom(buttonID, SHOW_MAP));
    }

    @ButtonHandler(value = SHOW_PLAYER_AREAS, save = false)
    public static void showPlayArea(Game game, ButtonInteractionEvent event) {
        showMapPart(game, event, DisplayType.stats, null);
    }

    @ButtonHandler(value = SHOW_FULL_MAP, save = false)
    public static void showFullMap(Game game, ButtonInteractionEvent event, String buttonID) {
        showMapPart(game, event, DisplayType.all, MapSegmentService.segmentFrom(buttonID, SHOW_FULL_MAP));
    }
}
