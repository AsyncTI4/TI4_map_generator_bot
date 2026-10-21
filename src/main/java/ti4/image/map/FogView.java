package ti4.image.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.service.testbed.TestBedService;

final class FogView {

    private final Game game;
    private final boolean privateView;

    @Nullable
    private final Player viewer;

    private final Set<String> visibleSystems;
    private final Map<String, Tile> tiles;

    private FogView(
            Game game,
            boolean privateView,
            @Nullable Player viewer,
            Set<String> visibleSystems,
            Map<String, Tile> tiles) {
        this.game = game;
        this.privateView = privateView;
        this.viewer = viewer;
        this.visibleSystems = visibleSystems;
        this.tiles = tiles;
    }

    static FogView of(Game game, @Nullable GenericInteractionCreateEvent event) {
        if (!isPrivateViewRequested(game, event)) {
            return unfogged(game);
        }
        Player viewer = TestBedService.resolveActingPlayer(
                game,
                event,
                CommandHelper.getPlayerFromGame(
                        game, event.getMember(), event.getUser().getId()));
        Set<String> visibleSystems = FoWHelper.fowFilter(game, viewer);
        Map<String, Tile> tiles = new HashMap<>(game.getTileMap());
        Set<String> hiddenPositions = new HashSet<>(tiles.keySet());
        hiddenPositions.removeAll(visibleSystems);
        for (String position : hiddenPositions) {
            tiles.remove(position);
            if (viewer != null) {
                Tile fogTile = viewer.buildFogTile(position, viewer);
                if (fogTile != null) {
                    tiles.put(position, fogTile);
                }
            }
        }
        return new FogView(game, true, viewer, visibleSystems, tiles);
    }

    static FogView unfogged(Game game) {
        return new FogView(game, false, null, Set.of(), new HashMap<>(game.getTileMap()));
    }

    private static boolean isPrivateViewRequested(Game game, @Nullable GenericInteractionCreateEvent event) {
        return game.isFowMode()
                && event != null
                && (FoWHelper.isPrivateGame(game, event)
                        || event instanceof UserOverridenGenericInteractionCreateEvent);
    }

    boolean isPrivate() {
        return privateView;
    }

    @Nullable
    Player viewer() {
        return viewer;
    }

    Set<String> visibleSystems() {
        return visibleSystems;
    }

    Map<String, Tile> tiles() {
        return tiles;
    }

    boolean shouldConvertToGeneric(Player player) {
        return privateView && !FoWHelper.canSeeStatsOfPlayer(game, player, viewer);
    }

    Set<String> seenPositions() {
        Set<String> positions = new HashSet<>();
        tiles.forEach((position, tile) -> {
            if (!isUnseenBlankFogTile(tile)) {
                positions.add(position);
            }
        });
        return positions;
    }

    boolean isUnseenBlankFogTile(Tile tile) {
        return privateView && "0b".equals(tile.getTileID()) && StringUtils.isBlank(tile.getFogLabel(viewer));
    }

    @Nullable
    String homeSystemPosition() {
        return privateView && viewer != null ? viewer.getHomeSystemPosition() : null;
    }
}
