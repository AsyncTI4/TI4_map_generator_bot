package ti4.image;

import static ti4.image.TileGeometry.LAYER_SIZE;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DisplayType;

public final class TileLayers {

    private static final Map<TileStep, TileStepPainter> PAINTERS = painters();

    private final TileScene scene;

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
        return new TileLayers(new TileScene(game, displayType, TileViewer.resolve(game, event, explicitViewer)));
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

    TileViewer viewer() {
        return scene.viewer();
    }

    public BufferedImage draw(Tile tile, TileStep step) {
        BufferedImage layerImage = new BufferedImage(LAYER_SIZE, LAYER_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = layerImage.createGraphics();
        TileLayer layer = new TileLayer(tile, tile.getTileModel().getShipPositionsType(), g);
        TileStepPainter painter = PAINTERS.get(step);
        if (painter != null) {
            painter.paint(scene, layer);
        }
        return layerImage;
    }

    void paint(Tile tile, TileStep step, Graphics target, int x, int y) {
        target.drawImage(draw(tile, step), x, y, null);
    }
}
