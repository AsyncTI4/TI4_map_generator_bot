package ti4.service.fow;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.image.CompactOverviewGenerator;
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

    @ButtonHandler(OVERVIEW_BUTTON_ID)
    public static void showOverview(ButtonInteractionEvent event, Game game) {
        switch (viewOf(game, event)) {
            case GM ->
                MessageHelper.sendFileUploadToChannel(
                        event.getMessageChannel(), CompactOverviewGenerator.gmOverview(game));
            case PLAYER ->
                MessageHelper.sendFileUploadToChannel(
                        event.getMessageChannel(),
                        CompactOverviewGenerator.playerOverview(game, viewer(game, event), event));
            case NONE -> MessageHelper.sendEphemeralMessageToEventChannel(event, "The overview isn't available here.");
        }
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
