package ti4.image.tile;

import static ti4.image.tile.TileGeometry.LAYER_SIZE;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.service.fow.FogMemoryService;
import ti4.service.fow.FogViewer;

public final class TileLayers {

    private static final Map<TileStep, TileStepPainter> PAINTERS = painters();

    private static final Rectangle FULL_LAYER = new Rectangle(0, 0, LAYER_SIZE, LAYER_SIZE);

    private final TileScene scene;
    private BufferedImage scratch;
    private Rectangle scratchDirty;

    private TileLayers(TileScene scene) {
        this.scene = scene;
    }

    public static TileLayers of(
            Game game, @Nullable GenericInteractionCreateEvent event, @Nullable DisplayType displayType) {
        return of(game, event, displayType, null);
    }

    public static TileLayers of(
            Game game,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable DisplayType displayType,
            @Nullable Player explicitViewer) {
        FogViewer viewer = FogViewer.resolve(game, event, explicitViewer);
        FogMemoryService.refresh(game, viewer);
        return forPreparedViewer(game, viewer, displayType);
    }

    public static TileLayers forPreparedViewer(Game game, FogViewer viewer, @Nullable DisplayType displayType) {
        return new TileLayers(new TileScene(game, displayType, viewer));
    }

    private static Map<TileStep, TileStepPainter> painters() {
        Map<TileStep, TileStepPainter> painters = new EnumMap<>(TileStep.class);
        painters.put(TileStep.Tile, new TileFaceStep(true));
        painters.put(TileStep.TileUnannotated, new TileFaceStep(false));
        painters.put(TileStep.Extras, new BorderDecorationsStep());
        painters.put(TileStep.Units, new UnitsStep());
        painters.put(TileStep.Distance, new DistanceStep());
        painters.put(TileStep.Wormholes, new WormholesStep());
        painters.put(TileStep.Anomalies, new AnomaliesStep());
        painters.put(TileStep.Aetherstream, new AetherstreamStep());
        painters.put(TileStep.Exile, new ExileStep());
        painters.put(TileStep.Legendaries, new LegendariesStep());
        painters.put(TileStep.Empties, new EmptiesStep());
        painters.put(TileStep.SpaceCannon, new SpaceCannonStep());
        painters.put(TileStep.Traits, new TraitsStep());
        painters.put(TileStep.TechSkips, new TechSkipsStep());
        painters.put(TileStep.Attachments, new AttachmentsStep());
        painters.put(TileStep.TileNumber, new TileNumberStep());
        painters.put(TileStep.Nucleus, new NucleusStep());
        return painters;
    }

    FogViewer viewer() {
        return scene.viewer();
    }

    public BufferedImage draw(Tile tile, TileStep step) {
        BufferedImage layerImage = newLayerImage();
        renderInto(layerImage, tile, step);
        return layerImage;
    }

    public void paint(Tile tile, TileStep step, Graphics2D target, int x, int y) {
        BufferedImage layerImage = clearedScratch();
        scratchDirty = FULL_LAYER;
        renderInto(layerImage, tile, step);
        Rectangle painted = PaintedBounds.of(layerImage);
        scratchDirty = painted;
        if (painted == null) {
            return;
        }
        if (isIntegerTranslation(target.getTransform())) {
            BufferedImage paintedPart = layerImage.getSubimage(painted.x, painted.y, painted.width, painted.height);
            target.drawImage(paintedPart, x + painted.x, y + painted.y, null);
        } else {
            target.drawImage(layerImage, x, y, null);
        }
    }

    private void renderInto(BufferedImage layerImage, Tile tile, TileStep step) {
        Graphics2D g = layerImage.createGraphics();
        try {
            TileLayer layer = new TileLayer(tile, tile.getTileModel().getShipPositionsType(), g);
            TileStepPainter painter = PAINTERS.get(step);
            if (painter != null) {
                painter.paint(scene, layer);
            }
        } finally {
            g.dispose();
        }
    }

    private BufferedImage clearedScratch() {
        if (scratch == null) {
            scratch = newLayerImage();
        } else if (scratchDirty != null) {
            Graphics2D g = scratch.createGraphics();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(scratchDirty.x, scratchDirty.y, scratchDirty.width, scratchDirty.height);
            g.dispose();
        }
        scratchDirty = null;
        return scratch;
    }

    private static BufferedImage newLayerImage() {
        return new BufferedImage(LAYER_SIZE, LAYER_SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    private static boolean isIntegerTranslation(AffineTransform transform) {
        int type = transform.getType();
        boolean translationOnly = (type & ~AffineTransform.TYPE_TRANSLATION) == 0;
        return translationOnly
                && transform.getTranslateX() == Math.rint(transform.getTranslateX())
                && transform.getTranslateY() == Math.rint(transform.getTranslateY());
    }
}
