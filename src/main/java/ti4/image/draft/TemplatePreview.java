package ti4.image.draft;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.DisplayType;
import ti4.helpers.MapTemplateHelper;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.TextWrap;
import ti4.image.io.Fonts;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.model.MapPositionMapper;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;

public final class TemplatePreview {

    private static final int TILE_SPACE = 300;
    private static final int DESCRIPTION_WIDTH = 650;

    private TemplatePreview() {}

    public static BufferedImage render(GenericInteractionCreateEvent event, Game game, MapTemplateModel model) {
        TileLayers tileLayers = TileLayers.of(game, event, DisplayType.all);

        int size = (model.numRings() * 2 + 1) * TILE_SPACE + 200;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();

        Point mecatol = MapPositionMapper.getTilePosition("000");
        if (mecatol == null) mecatol = new Point(0, 0);
        Point center = new Point(size / 2 - 173 - mecatol.x, size / 2 - 150 - mecatol.y);

        for (MapTemplateTile templateTile : model.getTemplateTiles()) {
            Point position = MapPositionMapper.getTilePosition(templateTile.getPos());
            if (position == null) continue;

            Tile tile = MapTemplateHelper.getTileFromTemplateTile(templateTile);
            if (tile != null) {
                BufferedImage tileImage = tileLayers.draw(tile, TileStep.Tile);
                graphics.drawImage(tileImage, position.x + center.x - 100, position.y + center.y - 100, null);
            }
        }
        drawCaption(graphics, model);
        return image;
    }

    private static void drawCaption(Graphics2D graphics, MapTemplateModel model) {
        graphics.setFont(Fonts.ti(80));
        int y = 100;
        OutlinedText.draw(graphics, "Template: " + model.getAlias(), 10, y, null, null, null, null, null);
        y += 100;
        if (model.getAuthor() != null) {
            graphics.setFont(Fonts.ti(64));
            OutlinedText.draw(graphics, "Author: " + model.getAuthor(), 10, y, null, null, null, null, null);
            y += 70;
        }
        if (model.getDescr() != null) {
            graphics.setFont(Fonts.ti(48));
            List<String> lines = TextWrap.layoutLines(model.getDescr(), graphics.getFontMetrics(), DESCRIPTION_WIDTH);
            for (String line : lines) {
                OutlinedText.draw(graphics, line, 20, y, null, null, null, null, null);
                y += 70;
            }
        }
    }
}
