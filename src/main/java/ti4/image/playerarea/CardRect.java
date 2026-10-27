package ti4.image.playerarea;

import ti4.model.ModelInterface;

final class CardRect {

    static final int WIDTH = 44;
    static final int HEIGHT = 152;

    private CardRect() {}

    static void draw(PlayerAreaContext ctx, int x, int y, int width, int height, ModelInterface dataModel) {
        ctx.g2().drawRect(x, y, width, height);
        if (dataModel == null) {
            ctx.overlays().add("missingDataModel", "missingDataModel", x, y, width, height);
        } else {
            ctx.overlays().add(dataModel, x, y, width, height);
        }
    }

    static void draw(PlayerAreaContext ctx, int x, int y, int width, int height, String title, String text) {
        ctx.g2().drawRect(x, y, width, height);
        ctx.overlays().add(title, text, x, y, width, height);
    }
}
