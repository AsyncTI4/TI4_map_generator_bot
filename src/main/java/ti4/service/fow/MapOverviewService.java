package ti4.service.fow;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.image.CompactOverviewGenerator;
import ti4.image.MapRenderPipeline;
import ti4.message.MessageHelper;
import ti4.service.option.FOWOptionService.FOWOption;

@UtilityClass
public class MapOverviewService {

    static final String OVERVIEW_BUTTON_ID = "compactMapOverview";

    public static Optional<Button> overviewButton(Game game, GenericInteractionCreateEvent event) {
        if (viewOf(game, event) == View.NONE) {
            return Optional.empty();
        }
        return Optional.of(Buttons.green(OVERVIEW_BUTTON_ID, "Overview"));
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
                () -> view == View.GM
                        ? CompactOverviewGenerator.gmOverview(game)
                        : CompactOverviewGenerator.playerOverview(game, viewer, event),
                fileUpload -> {
                    if (replyPrivately) {
                        MessageHelper.sendEphemeralFileInResponseToButtonPress(fileUpload, event);
                    } else {
                        MessageHelper.sendFileUploadToChannel(channel, fileUpload);
                    }
                });
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
