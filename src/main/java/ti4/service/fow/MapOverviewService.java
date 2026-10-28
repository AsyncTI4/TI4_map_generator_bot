package ti4.service.fow;

import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.image.map.CompactOverviewGenerator;
import ti4.image.map.MapRenderPipeline;
import ti4.message.MessageHelper;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class MapOverviewService {

    static final String OVERVIEW_BUTTON_ID = "compactMapOverview";
    static final String GM_OVERVIEW_BUTTON_ID = "compactMapGmOverview";

    public static boolean showsOverlayAsOverview(Game game, DisplayType displayType) {
        return game.isFowMode()
                && !game.getFowOption(FOWOption.CLASSIC_MAP_LAYOUT)
                && CompactOverviewGenerator.drawsOverlay(displayType);
    }

    public static void queueOverlayOverview(
            Game game, @Nullable Player viewer, DisplayType overlay, Consumer<FileUpload> callback) {
        String viewerId = viewer == null ? null : viewer.getUserID();
        MapRenderPipeline.queueImage(
                game,
                "Map overview",
                snapshot -> viewerId == null
                        ? CompactOverviewGenerator.gmOverview(snapshot, overlay)
                        : CompactOverviewGenerator.playerOverview(snapshot, snapshot.getPlayer(viewerId), overlay),
                callback);
    }

    public static Optional<Button> overviewButton(Game game, GenericInteractionCreateEvent event) {
        if (viewOf(game, event) == View.NONE) {
            return Optional.empty();
        }
        return Optional.of(Buttons.green(OVERVIEW_BUTTON_ID, "Overview"));
    }

    public static Optional<Button> gmOverviewButton(
            Game game, GenericInteractionCreateEvent event, MessageChannel postedIn) {
        if (!isGameMasterInOwnPrivateChannel(game, event, postedIn)) {
            return Optional.empty();
        }
        return Optional.of(Buttons.gray(GM_OVERVIEW_BUTTON_ID, "GM Overview"));
    }

    @ButtonHandler(value = GM_OVERVIEW_BUTTON_ID, save = false)
    public static void showGmOverview(ButtonInteractionEvent event, Game game) {
        MessageChannel channel = event.getMessageChannel();
        if (!isGameMasterInOwnPrivateChannel(game, event, channel)) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Only the GM can open the GM overview, from their own private channel.");
            return;
        }
        MapRenderPipeline.queueImage(
                game,
                "GM map overview",
                CompactOverviewGenerator::gmOverview,
                fileUpload -> MessageHelper.sendFileUploadToChannel(channel, fileUpload));
    }

    private static boolean isGameMasterInOwnPrivateChannel(
            Game game, GenericInteractionCreateEvent event, MessageChannel postedIn) {
        if (viewOf(game, event) != View.PLAYER) {
            return false;
        }
        String userId = event.getUser().getId();
        Player gameMaster = game.getPlayer(userId);
        return FoWHelper.isGameMaster(userId, game)
                && gameMaster != null
                && isSameChannel(gameMaster.getPrivateChannel(), postedIn);
    }

    private static boolean isSameChannel(@Nullable MessageChannel expected, MessageChannel channel) {
        if (expected == null) {
            return false;
        }
        MessageChannel checked = channel instanceof ThreadChannel thread ? thread.getParentMessageChannel() : channel;
        return expected.getId().equals(checked.getId());
    }

    @ButtonHandler(value = OVERVIEW_BUTTON_ID, save = false)
    public static void showOverview(ButtonInteractionEvent event, Game game) {
        View view = viewOf(game, event);
        if (view == View.NONE) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "The overview isn't available here.");
            return;
        }
        Player viewer = viewer(game, event);
        MessageChannel channel = event.getMessageChannel();
        boolean replyPrivately = view == View.PLAYER && isSharedChannel(game, channel);
        MapRenderPipeline.queueImage(
                game,
                "Map overview",
                snapshot -> view == View.GM
                        ? CompactOverviewGenerator.gmOverview(snapshot)
                        : CompactOverviewGenerator.playerOverview(snapshot, sameSeat(snapshot, viewer)),
                fileUpload -> {
                    if (replyPrivately) {
                        MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event);
                    } else {
                        MessageHelper.sendFileUploadToChannel(channel, fileUpload);
                    }
                });
    }

    private static Player sameSeat(Game snapshot, Player viewer) {
        return viewer == null ? null : snapshot.getPlayer(viewer.getUserID());
    }

    private static boolean isSharedChannel(Game game, MessageChannel channel) {
        MessageChannel shared = game.getMainGameChannel();
        if (shared == null) {
            return false;
        }
        MessageChannel checked = channel instanceof ThreadChannel thread ? thread.getParentMessageChannel() : channel;
        return shared.getId().equals(checked.getId());
    }

    private enum View {
        GM,
        PLAYER,
        NONE
    }

    private static View viewOf(Game game, GenericInteractionCreateEvent event) {
        if (game == null || !game.isFowMode() || game.getFowOption(FOWOption.CLASSIC_MAP_LAYOUT)) {
            return View.NONE;
        }
        if (FoWHelper.canSeeWholeMap(game, event)) {
            return View.GM;
        }
        Player viewer = viewer(game, event);
        if (MapSegmentService.isFoggedView(game, event) && viewer != null && viewer.isRealPlayer()) {
            return View.PLAYER;
        }
        return View.NONE;
    }

    private static Player viewer(Game game, GenericInteractionCreateEvent event) {
        return game.getPlayer(MapSegmentService.viewerId(game, event));
    }
}
