package ti4.service.fow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.image.MapSegment;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.testbed.TestBedService;

@UtilityClass
public class MapSegmentService {

    private static final String SWITCH_PREFIX = "showMapSegment_";

    public static String withSegment(String buttonId, @Nullable String segment) {
        return segment == null ? buttonId : buttonId + "_" + segment;
    }

    @Nullable
    public static String segmentFrom(String buttonId, String baseId) {
        String suffix = StringUtils.substringAfter(buttonId, baseId + "_");
        return MapSegment.isValidName(suffix) ? suffix : null;
    }

    public static boolean isFoggedView(Game game, GenericInteractionCreateEvent event) {
        return game.isFowMode()
                && (event instanceof UserOverridenGenericInteractionCreateEvent
                        || FoWHelper.isPrivateGame(game, event));
    }

    public static boolean isFoggedView(Game game, @Nullable Channel channel) {
        return game.isFowMode() && channel != null && FoWHelper.isPrivateGame(game, null, channel);
    }

    public static List<String> viewableNames(Game game, String userId, boolean foggedView) {
        List<String> names = new ArrayList<>(visibleTo(game, userId, foggedView).stream()
                .map(MapSegment::name)
                .toList());
        if (!names.isEmpty() && MapSegment.mainMapVisibleTo(game, foggedViewer(game, userId, foggedView))) {
            names.addFirst(MapSegment.MAIN);
        }
        return names;
    }

    public static List<Button> switchButtons(Game game, String userId, boolean foggedView) {
        List<String> names = viewableNames(game, userId, foggedView);
        if (names.size() < 2) {
            return List.of();
        }
        Map<String, String> labels = MapSegment.all(game).stream()
                .collect(Collectors.toMap(
                        MapSegment::name, segment -> segment.displayName(game), (first, second) -> first));
        labels.put(MapSegment.MAIN, MapSegment.mainDisplayName(game));
        return names.stream()
                .map(name -> Buttons.gray(SWITCH_PREFIX + name, "Map: " + labels.getOrDefault(name, name)))
                .toList();
    }

    @Nullable
    private static Player foggedViewer(Game game, String userId, boolean foggedView) {
        if (!foggedView && FoWHelper.isGameMaster(userId, game)) {
            return null;
        }
        return game.getPlayer(userId);
    }

    private static List<MapSegment> visibleTo(Game game, String userId, boolean foggedView) {
        if (!game.isFowMode() || game.getFowOption(FOWOption.CLASSIC_MAP_LAYOUT)) {
            return List.of();
        }
        if (!foggedView && FoWHelper.isGameMaster(userId, game)) {
            return MapSegment.all(game);
        }
        Player player = game.getPlayer(userId);
        if (player == null || !game.getRealPlayers().contains(player)) {
            return List.of();
        }
        return MapSegment.visibleTo(game, player);
    }

    public static String viewerId(Game game, GenericInteractionCreateEvent event) {
        Player acting = TestBedService.resolveActingPlayer(game, event, null);
        return acting != null ? acting.getUserID() : event.getUser().getId();
    }

    @ButtonHandler(value = SWITCH_PREFIX, save = false)
    public static void showSegment(ButtonInteractionEvent event, String buttonID, Game game) {
        String name = buttonID.substring(SWITCH_PREFIX.length());
        boolean foggedView = isFoggedView(game, event);
        if (!viewableNames(game, viewerId(game, event), foggedView).contains(name)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That part of the map is not available to you.");
            return;
        }
        ShowGameService.simpleShowGame(game, event, DisplayType.all, MapSegment.MAIN.equals(name) ? null : name);
    }
}
