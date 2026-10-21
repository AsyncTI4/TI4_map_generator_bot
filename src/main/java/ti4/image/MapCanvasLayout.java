package ti4.image;

import ti4.game.Game;
import ti4.helpers.DisplayType;

final class MapCanvasLayout {

    static final int EXPEDITION_TRACKER_RIGHT_OFFSET = 1200;

    private static final int STATS_ONLY_GAME_INFO_HEIGHT = 40;
    private static final int LANDSCAPE_PLAYER_COLUMNS_WIDTH = 4 * 520;

    private final int width;
    private final int height;
    private final int heightForGameInfo;
    private final int playerAreasWidth;
    private final int mapWidth;
    private final int stripWidth;
    private final boolean landscape;

    private MapCanvasLayout(
            int width,
            int height,
            int heightForGameInfo,
            int playerAreasWidth,
            int mapWidth,
            int stripWidth,
            boolean landscape) {
        this.width = width;
        this.height = height;
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
        boolean megaGame = PlayerAreaGenerator.isMegaGame(game);
        int playerAreasWidth =
                megaGame ? Math.max(mapWidth, PlayerAreasHeightEstimate.megaGamePlayerAreasMinimumWidth()) : mapWidth;
        int heightOfPlayerAreasSection =
                new PlayerAreasHeightEstimate(game, fog, neighbours, playerAreasWidth).heightOfPlayerAreasSection();

        int heightForGameInfo;
        int height;
        int width;
        switch (basicType) {
            case stats:
                heightForGameInfo = STATS_ONLY_GAME_INFO_HEIGHT;
                height = heightOfPlayerAreasSection;
                width = playerAreasWidth;
                break;
            case map:
                heightForGameInfo = mapHeight;
                height = mapHeight + BoardLayout.SPACE_FOR_TILE_HEIGHT * 2;
                width = board.framedWidth();
                break;
            default:
                if (displayType == DisplayType.landscape) {
                    heightForGameInfo = STATS_ONLY_GAME_INFO_HEIGHT;
                    height = Math.max(heightOfPlayerAreasSection, mapHeight);
                    width = landscapeWidth(mapWidth);
                } else {
                    heightForGameInfo = mapHeight;
                    height = mapHeight + heightOfPlayerAreasSection;
                    width = megaGame ? Math.max(board.framedWidth(), playerAreasWidth) : board.framedWidth();
                }
        }
        int stripWidth = basicType == DisplayType.map ? width : mapWidth;
        return new MapCanvasLayout(
                width,
                height,
                heightForGameInfo,
                playerAreasWidth,
                mapWidth,
                stripWidth,
                displayType == DisplayType.landscape);
    }

    private static int landscapeWidth(int mapWidth) {
        return mapWidth + LANDSCAPE_PLAYER_COLUMNS_WIDTH + BoardLayout.EXTRA_X * 2;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
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
