package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.image.draw.HexOverlays;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.Mapper;

final class NucleusStep implements TileStepPainter {

    // Matches the draft tile colours MapTemplateHelper.getTileFromTemplateTile gives each player number
    // (it always skips "red"); 3 is emerald and 6 chocolate there.
    private static final Map<Integer, String> NUCLEUS_SLICE_COLORS = Map.of(
            1, "blue",
            2, "yellow",
            3, "green",
            4, "lavender",
            5, "petrol",
            6, "brown",
            7, "ethereal",
            8, "forest");
    private static final List<Integer> ALL_SIDES = List.of(0, 1, 2, 3, 4, 5);
    private static final float SLICE_BORDER_WIDTH = 2.5f;
    private static final float SEAT_BORDER_WIDTH = 4.0f;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        MapTemplateTile mapTile = templateTile(game, tile);

        TileArtwork.draw(game, layer);
        Graphics2D g = layer.g();
        List<Integer> nucleusNumbers = mapTile.getNucleusNumbers();
        boolean inNucleus = nucleusNumbers != null && !nucleusNumbers.isEmpty();
        if (inNucleus) {
            drawSliceBorders(g, nucleusNumbers);
        }
        if (!tile.getTileModel().isHyperlane() && !inNucleus) {
            HighlightGuards.fogOut(layer);
        }
        if (mapTile.getHome() != null && mapTile.getHome() && mapTile.getPlayerNumber() != null) {
            String color = NUCLEUS_SLICE_COLORS.getOrDefault(mapTile.getPlayerNumber(), "black");
            BufferedImage border = HexOverlays.border("solid", Mapper.getColor(color), List.of(), SEAT_BORDER_WIDTH);
            g.drawImage(border, TILE_PADDING, TILE_PADDING, null);
        }
    }

    private static MapTemplateTile templateTile(Game game, Tile tile) {
        String mapTemplateId = game.getMapTemplateID();
        MapTemplateModel mapTemplate = mapTemplateId != null ? Mapper.getMapTemplate(mapTemplateId) : null;
        if (mapTemplate == null || !mapTemplate.isNucleusTemplate()) {
            throw new IllegalStateException("Cannot generate Nucleus display without a Nucleus map template");
        }
        MapTemplateTile mapTile = mapTemplate.getTemplateTiles().stream()
                .filter(t -> t.getPos().equals(tile.getPosition()))
                .findFirst()
                .orElse(null);
        if (mapTile == null) {
            throw new IllegalStateException(
                    "Cannot find tile " + tile.getPosition() + " in Nucleus map template " + mapTemplate.getAlias());
        }
        return mapTile;
    }

    private static void drawSliceBorders(Graphics2D g, List<Integer> nucleusNumbers) {
        int nucleusNumberIndex = 0;
        Map<Integer, List<Integer>> nucleusNumberToSides = new HashMap<>();
        for (int side = 0; side < 6; ++side) {
            Integer nucleusNumber = nucleusNumbers.get(nucleusNumberIndex);
            nucleusNumberIndex = (nucleusNumberIndex + 1) % nucleusNumbers.size();
            nucleusNumberToSides.putIfAbsent(nucleusNumber, new ArrayList<>());
            nucleusNumberToSides.get(nucleusNumber).add(side);
        }
        for (Map.Entry<Integer, List<Integer>> entry : nucleusNumberToSides.entrySet()) {
            String color = NUCLEUS_SLICE_COLORS.getOrDefault(entry.getKey(), "black");
            List<Integer> openSides = new ArrayList<>(ALL_SIDES);
            openSides.removeAll(entry.getValue());
            BufferedImage border = HexOverlays.border("solid", Mapper.getColor(color), openSides, SLICE_BORDER_WIDTH);
            g.drawImage(border, TILE_PADDING, TILE_PADDING, null);
        }
    }
}
