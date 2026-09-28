package ti4.service.fow;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;
import ti4.helpers.FoWHelper;
import ti4.image.MapSegment;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;

@UtilityClass
public class MapSegmentService {

    private static final String SWITCH_PREFIX = "showMapSegment_";

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
        boolean fractureIsTheOnlySector = MapSegment.all(game).stream().allMatch(MapSegment::isFracture);
        if (fractureIsTheOnlySector && names.contains(MapSegment.FRACTURE)) {
            names.addFirst(MapSegment.MAIN);
        }
        return names;
    }

    public static List<Button> switchButtons(Game game, String userId, boolean foggedView) {
        List<String> names = viewableNames(game, userId, foggedView);
        if (names.size() < 2) {
            return List.of();
        }
        return names.stream()
                .map(name -> Buttons.gray(SWITCH_PREFIX + name, "Map: " + name))
                .toList();
    }

    private static List<MapSegment> visibleTo(Game game, String userId, boolean foggedView) {
        if (!game.isFowMode()) {
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

    @ButtonHandler(value = SWITCH_PREFIX, save = false)
    public static void showSegment(ButtonInteractionEvent event, String buttonID, Game game) {
        String name = buttonID.substring(SWITCH_PREFIX.length());
        boolean foggedView = isFoggedView(game, event);
        if (!viewableNames(game, event.getUser().getId(), foggedView).contains(name)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That part of the map is not available to you.");
            return;
        }
        ShowGameService.simpleShowGame(game, event, DisplayType.all, MapSegment.MAIN.equals(name) ? null : name);
    }
}
