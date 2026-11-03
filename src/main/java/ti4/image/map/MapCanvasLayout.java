package ti4.image.map;

import ti4.game.Game;
import ti4.helpers.DisplayType;
import ti4.image.playerarea.NeighbourMap;
import ti4.image.playerarea.PlayerAreaColumns;
import ti4.image.tile.TileGeometry;

final class MapCanvasLayout {

    static final int EXPEDITION_TRACKER_RIGHT_OFFSET = 1200;

    private static final int STATS_ONLY_GAME_INFO_HEIGHT = 40;
    private static final int LANDSCAPE_PLAYER_COLUMNS_WIDTH = 4 * 520;
    private static final int ALLOCATION_SLACK_DIVISOR = 5;
    private static final int BOTTOM_MARGIN = 20;

    private final int width;
    private final int estimatedHeight;
    private final int minimumHeight;
    private final boolean heightFollowsContent;
    private final int heightForGameInfo;
    private final int playerAreasWidth;
    private final int mapWidth;
    private final int stripWidth;
    private final boolean landscape;

    private MapCanvasLayout(
            int width,
            int estimatedHeight,
            int minimumHeight,
            boolean heightFollowsContent,
            int heightForGameInfo,
            int playerAreasWidth,
            int mapWidth,
            int stripWidth,
            boolean landscape) {
        this.width = width;
        this.estimatedHeight = estimatedHeight;
        this.minimumHeight = minimumHeight;
        this.heightFollowsContent = heightFollowsContent;
        this.heightForGameInfo = heightForGameInfo;
        this.playerAreasWidth = playerAreasWidth;
        this.mapWidth = mapWidth;
        this.stripWidth = stripWidth;
        this.landscape = landscape;
    }

    static MapCanvasLayout of(
            Game game,
            DisplayType displayType,
            DisplayType basicType,
            FogView fog,
            NeighbourMap neighbours,
            BoardLayout board) {
        int mapWidth = board.mapWidth();
        int mapHeight = board.mapHeight();
        boolean megaGame = PlayerAreaColumns.isMegaGame(game);
        boolean landscape = displayType == DisplayType.landscape;
        int playerAreasLeft = BoardLayout.PLAYER_AREAS_LEFT + (landscape ? mapWidth : 0);
        int playerAreasWidth = playerAreasWidth(landscape, megaGame, mapWidth);
        int heightOfPlayerAreasSection = new PlayerAreasHeightEstimate(
                        game, fog, neighbours, playerAreasLeft, playerAreasWidth)
                .heightOfPlayerAreasSection();

        int badgeRoom = GameModeBadges.isShown(game) ? GameModeBadges.ROOM_ABOVE_HEADER : 0;
        int heightForGameInfo;
        int height;
        int width;
        int minimumHeight;
        switch (basicType) {
            case stats:
                heightForGameInfo = STATS_ONLY_GAME_INFO_HEIGHT + badgeRoom;
                height = heightOfPlayerAreasSection + badgeRoom;
                minimumHeight = 0;
                width = playerAreasWidth;
                break;
            case map:
                heightForGameInfo = mapHeight;
                height = mapHeight + TileGeometry.TILE_HEIGHT * 2;
                minimumHeight = height;
                width = board.framedWidth();
                break;
            default:
                if (landscape) {
                    heightForGameInfo = STATS_ONLY_GAME_INFO_HEIGHT + badgeRoom;
                    height = Math.max(heightOfPlayerAreasSection + badgeRoom, mapHeight);
                    minimumHeight = mapHeight;
                    width = landscapeWidth(mapWidth);
                } else {
                    heightForGameInfo = mapHeight;
                    height = mapHeight + heightOfPlayerAreasSection;
                    minimumHeight = mapHeight;
                    width = megaGame ? Math.max(board.framedWidth(), playerAreasWidth) : board.framedWidth();
                }
        }
        int stripWidth = basicType == DisplayType.map ? width : mapWidth;
        return new MapCanvasLayout(
                width,
                height,
                minimumHeight,
                basicType != DisplayType.map,
                heightForGameInfo,
                playerAreasWidth,
                mapWidth,
                stripWidth,
                landscape);
    }

    private static int playerAreasWidth(boolean landscape, boolean megaGame, int mapWidth) {
        if (landscape) {
            return landscapeWidth(mapWidth);
        }
        return megaGame ? Math.max(mapWidth, PlayerAreasHeightEstimate.megaGamePlayerAreasMinimumWidth()) : mapWidth;
    }

    private static int landscapeWidth(int mapWidth) {
        return mapWidth + LANDSCAPE_PLAYER_COLUMNS_WIDTH + BoardLayout.EXTRA_X * 2;
    }

    int width() {
        return width;
    }

    boolean heightFollowsContent() {
        return heightFollowsContent;
    }

    int allocatedHeight() {
        if (!heightFollowsContent) {
            return estimatedHeight;
        }
        return estimatedHeight + estimatedHeight / ALLOCATION_SLACK_DIVISOR;
    }

    int heightFor(int contentBottom) {
        return Math.max(minimumHeight, contentBottom + BOTTOM_MARGIN);
    }

    int heightForGameInfo() {
        return heightForGameInfo;
    }

    int playerAreasWidth() {
        return playerAreasWidth;
    }

    int mapWidth() {
        return mapWidth;
    }

    int stripWidth() {
        return stripWidth;
    }

    boolean isLandscape() {
        return landscape;
    }

    int landscapeShift() {
        return landscape ? mapWidth : 0;
    }

    int landscapeRightEdge() {
        return landscapeWidth(mapWidth);
    }
}
