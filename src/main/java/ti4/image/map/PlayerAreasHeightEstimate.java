package ti4.image.map;

import java.util.List;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.playerarea.NeighbourMap;
import ti4.image.playerarea.PlayerAreaColumns;
import ti4.image.playerarea.PlayerAreaHeader;
import ti4.image.playerarea.ScoredSecrets;
import ti4.model.Mapper;

final class PlayerAreasHeightEstimate {

    static final int TYPICAL_PLAYER_AREA_HEIGHT = 340;

    private static final int LAW_COLUMNS = 2;
    private static final int LAW_HEIGHT = 115;
    private static final int OBJECTIVE_ROW_HEIGHT = 43;
    private static final int OBJECTIVE_ROWS_WITHOUT_EXTRA_HEIGHT = 5;
    private static final int HEIGHT_PER_20_UNREAL_PLAYERS = 15;

    private final Game game;
    private final FogView fog;
    private final NeighbourMap neighbours;
    private final int playerAreasLeft;
    private final int playerAreasRight;

    PlayerAreasHeightEstimate(
            Game game, FogView fog, NeighbourMap neighbours, int playerAreasLeft, int playerAreasRight) {
        this.game = game;
        this.fog = fog;
        this.neighbours = neighbours;
        this.playerAreasLeft = playerAreasLeft;
        this.playerAreasRight = playerAreasRight;
    }

    static int megaGamePlayerAreasMinimumWidth() {
        return BoardLayout.PLAYER_AREAS_LEFT
                + 2 * BoardLayout.smallestRegularPlayerAreaWidth()
                + PlayerAreaColumns.COLUMN_GAP;
    }

    int heightOfPlayerAreasSection() {
        return heightOfPlayerAreasSection(heightOfObjectivesSection());
    }

    private int heightOfObjectivesSection() {
        Set<String> revealedObjectives = game.getRevealedPublicObjectives().keySet();
        int stage1PublicObjCount = 0;
        int stage2PublicObjCount = 0;
        for (String objective : revealedObjectives) {
            if (Mapper.getPublicObjectivesStage1().containsKey(objective)) {
                stage1PublicObjCount++;
            } else if (Mapper.getPublicObjectivesStage2().containsKey(objective)) {
                stage2PublicObjCount++;
            }
        }
        int otherObjCount = revealedObjectives.size() - stage1PublicObjCount - stage2PublicObjCount;
        otherObjCount = Math.max(Objective.retrieveCustom(game).size(), otherObjCount);
        stage1PublicObjCount += game.getPublicObjectives1Peekable().size();
        stage2PublicObjCount += game.getPublicObjectives2Peekable().size();
        int mostObjectivesInAColumn = Math.max(Math.max(stage1PublicObjCount, stage2PublicObjCount), otherObjCount);
        return Math.max((mostObjectivesInAColumn - OBJECTIVE_ROWS_WITHOUT_EXTRA_HEIGHT) * OBJECTIVE_ROW_HEIGHT, 0);
    }

    private int heightOfPlayerAreasSection(int objectivesY) {
        int playersY = PlayerAreaColumns.isMegaGame(game)
                ? alignedPlayerAreaRowsHeight()
                : stackedPlayerAreasHeight() - hiddenPlayerAreasHeight();
        int unrealPlayers = game.getNotRealPlayers().size();
        playersY += Math.round(unrealPlayers / 20.0f) * HEIGHT_PER_20_UNREAL_PLAYERS;
        int lawsY = Math.max(
                (game.getLaws().size() / LAW_COLUMNS + 1) * LAW_HEIGHT, LawsAndEventsSection.heightOfLaws(game));
        lawsY += Math.max(
                (game.getEventsInEffect().size() / LAW_COLUMNS + 1) * LAW_HEIGHT,
                LawsAndEventsSection.heightOfEvents(game));
        return playersY + lawsY + objectivesY + BoardLayout.EXTRA_Y * 3;
    }

    private int stackedPlayerAreasHeight() {
        int playerCountForMap = game.getRealPlayers().size() + game.getDummies().size();
        if (game.getRealPlayers().size() > game.getRealPlayersNNeutral().size()) {
            playerCountForMap--;
        }
        int playersY = playerCountForMap * TYPICAL_PLAYER_AREA_HEIGHT;
        for (Player player : game.getPlayers().values()) {
            if (hasNoPlayerArea(player)) {
                playersY -= 350;
            }
        }
        for (Player player : game.getRealAndEliminatedPlayers()) {
            playersY += playerAreaHeightAdjustment(player, playerAreasRight - playerAreasLeft);
        }
        return playersY;
    }

    private int alignedPlayerAreaRowsHeight() {
        List<Player> shownPlayers = game.getRealAndEliminatedPlayers().stream()
                .filter(player -> !hasNoPlayerArea(player) && !fog.shouldConvertToGeneric(player))
                .toList();
        List<List<Player>> columns = PlayerAreaColumns.split(game, shownPlayers);
        int columnWidth = PlayerAreaColumns.columnWidth(playerAreasRight, playerAreasLeft, columns.size());
        int height = 0;
        for (int row = 0; row < columns.getFirst().size(); row++) {
            int rowHeight = 0;
            for (List<Player> column : columns) {
                if (row < column.size()) {
                    rowHeight = Math.max(
                            rowHeight,
                            TYPICAL_PLAYER_AREA_HEIGHT + playerAreaHeightAdjustment(column.get(row), columnWidth));
                }
            }
            height += rowHeight;
        }
        return height;
    }

    private static boolean hasNoPlayerArea(Player player) {
        return "neutral".equalsIgnoreCase(player.getFaction()) || (player.isNpc() && player.isDummy());
    }

    private int playerAreaHeightAdjustment(Player player, int areaWidth) {
        boolean headerCanGrow =
                PlayerAreaColumns.isMegaGame(game) || player.getTeamMateIDs().size() > 1;
        int adjustment = headerCanGrow
                ? PlayerAreaHeader.extraHeight(game, neighbours, player, playerAreasLeft, playerAreasLeft + areaWidth)
                : 0;
        if (player.isEliminated()) {
            return adjustment - 190;
        }
        int secretRows = ScoredSecrets.rowCount(game, player);
        if (secretRows >= 4) {
            return adjustment + (secretRows - 4) * 43 + 23;
        }
        return adjustment;
    }

    private int hiddenPlayerAreasHeight() {
        if (!fog.isPrivate() || fog.viewer() == null) {
            return 0;
        }
        return game.getRealAndEliminatedPlayers().stream()
                .filter(player -> !hasNoPlayerArea(player) && fog.shouldConvertToGeneric(player))
                .mapToInt(player -> TYPICAL_PLAYER_AREA_HEIGHT
                        + playerAreaHeightAdjustment(player, playerAreasRight - playerAreasLeft))
                .sum();
    }
}
