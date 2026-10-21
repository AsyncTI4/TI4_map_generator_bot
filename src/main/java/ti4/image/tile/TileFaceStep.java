package ti4.image.tile;

final class TileFaceStep implements TileStepPainter {

    private final boolean annotated;

    TileFaceStep(boolean annotated) {
        this.annotated = annotated;
    }

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        TileArtwork.draw(scene.game(), layer);
        if (!annotated) {
            return;
        }
        ControlBorder.draw(scene, layer);
        TileText.applyTextSize(layer.g(), scene.game());
        FogOverlay.draw(scene, layer);
        DraftTileLabel.draw(layer);
        LawWormholeMarks.draw(scene.game(), layer);
    }
}
