package ti4.image;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.helpers.DisplayType;
import ti4.model.ModelInterface;
import ti4.website.model.WebsiteOverlay;

public class MapGenerator implements AutoCloseable {

    private static final BasicStroke stroke2 = new BasicStroke(2.0f);
    private static final BasicStroke stroke3 = new BasicStroke(3.0f);
    private static final BasicStroke stroke4 = new BasicStroke(4.0f);
    private static final BasicStroke stroke5 = new BasicStroke(5.0f);
    private static final BasicStroke stroke6 = new BasicStroke(6.0f);

    private final RenderContext ctx;
    private final Graphics2D graphics;
    private final BufferedImage mainImage;
    private final GenericInteractionCreateEvent event;
    private final int scoreTokenSpacing;
    private final Game game;
    private final DisplayType displayType;
    private final DisplayType displayTypeBasic;
    private final RenderTimings timings;
    private final int width;
    private final int height;
    private final int heightForGameInfo;
    private final FogView fog;
    private final BoardLayout board;
    private final WebsiteOverlays websiteOverlays;
    private final int mapWidth;
    private final int playerAreasWidth;
    private final int stripWidth;

    MapGenerator(Game game, @Nullable DisplayType displayType, @Nullable GenericInteractionCreateEvent event) {
        this(game, displayType, event, null);
    }

    MapGenerator(
            Game game,
            @Nullable DisplayType displayType,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable String requestedSegment) {
        this(
                RenderContext.build(new MapRenderRequest(game, displayType, event, requestedSegment)),
                RenderTimings.disabled());
    }

    MapGenerator(RenderContext ctx, RenderTimings timings) {
        this.ctx = ctx;
        this.timings = timings;
        game = ctx.game();
        event = ctx.event();
        displayTypeBasic = ctx.basicType();
        this.displayType = ctx.displayType();
        scoreTokenSpacing = ctx.scoreTokenSpacing();
        fog = ctx.fog();
        board = ctx.board();
        websiteOverlays = ctx.overlays();
        MapCanvasLayout canvas = ctx.canvas();
        width = canvas.width();
        height = canvas.height();
        heightForGameInfo = canvas.heightForGameInfo();
        mapWidth = canvas.mapWidth();
        playerAreasWidth = canvas.playerAreasWidth();
        stripWidth = canvas.stripWidth();

        mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        graphics = mainImage.createGraphics();
    }

    int imageWidth() {
        return width;
    }

    int imageHeight() {
        return height;
    }

    @Nullable
    String shownSegmentName() {
        return board.shownSegmentName();
    }

    Set<String> pinnedCorners() {
        return board.pinnedCorners();
    }

    int frameContentWidth() {
        return board.frameContentWidth();
    }

    Point pinnedCornerTileOrigin(String corner) {
        return board.pinnedCornerTileOrigin(corner);
    }

    boolean isInShownRegion(@Nullable String position) {
        return board.isInShownRegion(position);
    }

    BufferedImage drawCanvas() {
        paintCanvas();
        return mainImage;
    }

    List<WebsiteOverlay> overlays() {
        return websiteOverlays.asList();
    }

    private void paintCanvas() {
        timings.startTiles();
        BoardSection.draw(ctx, graphics);
        ConnectionStripSection.draw(ctx, graphics);
        timings.stopTiles();

        timings.startGraphics();
        drawImage();
    }

    private void drawImage() {
        int landscapeShift = ctx.canvas().landscapeShift();
        int y = HeaderSection.draw(ctx, graphics, landscapeShift);
        int x = BoardLayout.PLAYER_AREAS_LEFT + landscapeShift;
        int tempY = y;
        tempY = ScoreTrackSection.draw(ctx, graphics, landscapeShift, tempY + 20);
        if (PriorityTrackSection.isShown(game)) {
            tempY = PriorityTrackSection.draw(ctx, graphics, landscapeShift, tempY);
        }
        y = ObjectivesSection.draw(ctx, graphics, landscapeShift, tempY);
        y = LawsAndEventsSection.drawLaws(ctx, graphics, landscapeShift, y);
        y = LawsAndEventsSection.drawEvents(ctx, graphics, landscapeShift, y);
        if (MapSideStatsSection.isDrawn(ctx)) {
            MapSideStatsSection.draw(ctx, graphics);
        }

        if (displayTypeBasic == DisplayType.all || displayTypeBasic == DisplayType.stats) {
            Point topLeft = new Point(x, y);
            new PlayerAreaGenerator(
                            graphics,
                            game,
                            fog.isPrivate(),
                            fog.viewer(),
                            websiteOverlays,
                            playerAreasWidth,
                            scoreTokenSpacing,
                            ctx.neighbours())
                    .drawAllPlayerAreas(topLeft);
        }
    }

    @Override
    public void close() {
        graphics.dispose();
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int size) {
        PlayerAreaGenerator.drawPAImageScaled(graphics, x, y, resourceName, size, size);
    }

    public enum HorizontalAlign {
        Left,
        Center,
        Right
    }

    public enum VerticalAlign {
        Top,
        Center,
        Bottom
    }

    private void addWebsiteOverlay(String overlayTitle, String overlayText, int x, int y, int width, int height) {
        websiteOverlays.add(overlayTitle, overlayText, x, y, width, height);
    }

    private void addWebsiteOverlay(ModelInterface dataModel, int x, int y, int width, int height) {
        websiteOverlays.add(dataModel, x, y, width, height);
    }

    String getGameName() {
        return game.getName();
    }
}
