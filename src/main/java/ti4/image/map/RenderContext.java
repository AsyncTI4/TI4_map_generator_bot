package ti4.image.map;

import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.helpers.DisplayType;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.playerarea.NeighbourMap;
import ti4.image.playerarea.PlayerAreaWarnings;
import ti4.service.fow.FogViewer;

final class RenderContext {

    private final Game game;

    @Nullable
    private final GenericInteractionCreateEvent event;

    private final DisplayType displayType;
    private final DisplayType basicType;
    private final FogView fog;
    private final NeighbourMap neighbours;
    private final WebsiteOverlays overlays;
    private final MapSideStatLayout statLayout;
    private final int scoreTokenSpacing;
    private final BoardLayout board;
    private final MapCanvasLayout canvas;
    private PlayerAreaWarnings playerAreaWarnings = PlayerAreaWarnings.none();

    private RenderContext(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            DisplayType displayType,
            DisplayType basicType,
            FogView fog,
            NeighbourMap neighbours,
            WebsiteOverlays overlays,
            MapSideStatLayout statLayout,
            int scoreTokenSpacing,
            BoardLayout board,
            MapCanvasLayout canvas) {
        this.game = game;
        this.event = event;
        this.displayType = displayType;
        this.basicType = basicType;
        this.fog = fog;
        this.neighbours = neighbours;
        this.overlays = overlays;
        this.statLayout = statLayout;
        this.scoreTokenSpacing = scoreTokenSpacing;
        this.board = board;
        this.canvas = canvas;
    }

    static RenderContext build(MapRenderRequest request, FogViewer viewer) {
        Game game = request.game();
        NeighbourMap neighbours = new NeighbourMap(game);
        DisplayType displayType = effectiveDisplayType(game, request.displayType());
        DisplayType basicType = basicTypeOf(displayType);
        int scoreTokenSpacing = ObjectiveBox.scoreTokenSpacing();
        FogView fog = FogView.of(game, viewer);
        MapSideStatLayout statLayout = new MapSideStatLayout(game, fog);
        BoardLayout board = BoardLayout.resolve(
                game, displayType, basicType, request.segment(), fog, statLayout, scoreTokenSpacing);
        MapCanvasLayout canvas = MapCanvasLayout.of(game, displayType, basicType, fog, neighbours, board);
        return new RenderContext(
                game,
                request.event(),
                displayType,
                basicType,
                fog,
                neighbours,
                new WebsiteOverlays(),
                statLayout,
                scoreTokenSpacing,
                board,
                canvas);
    }

    private static DisplayType effectiveDisplayType(Game game, @Nullable DisplayType requested) {
        if (game.getDisplayTypeForced() != null) {
            return game.getDisplayTypeForced();
        }
        if (requested == null) {
            return DisplayType.all;
        }
        return requested;
    }

    private static DisplayType basicTypeOf(DisplayType displayType) {
        return switch (displayType) {
            case stats -> DisplayType.stats;
            case map,
                    wormholes,
                    anomalies,
                    legendaries,
                    empties,
                    aetherstream,
                    spacecannon,
                    traits,
                    techskips,
                    attachments,
                    shipless,
                    unlocked -> DisplayType.map;
            default -> DisplayType.all;
        };
    }

    Game game() {
        return game;
    }

    @Nullable
    GenericInteractionCreateEvent event() {
        return event;
    }

    DisplayType displayType() {
        return displayType;
    }

    DisplayType basicType() {
        return basicType;
    }

    boolean drawsBoard() {
        return basicType == DisplayType.all || basicType == DisplayType.map;
    }

    boolean isLandscape() {
        return canvas.isLandscape();
    }

    FogView fog() {
        return fog;
    }

    NeighbourMap neighbours() {
        return neighbours;
    }

    WebsiteOverlays overlays() {
        return overlays;
    }

    MapSideStatLayout statLayout() {
        return statLayout;
    }

    int scoreTokenSpacing() {
        return scoreTokenSpacing;
    }

    BoardLayout board() {
        return board;
    }

    MapCanvasLayout canvas() {
        return canvas;
    }

    PlayerAreaWarnings playerAreaWarnings() {
        return playerAreaWarnings;
    }

    void replacePlayerAreaWarnings(PlayerAreaWarnings warnings) {
        playerAreaWarnings = warnings;
    }
}
