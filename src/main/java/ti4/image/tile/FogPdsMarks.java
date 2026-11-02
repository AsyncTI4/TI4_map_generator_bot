package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PlayerTokens;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;

final class FogPdsMarks {

    private FogPdsMarks() {}

    static void paint(TileScene scene, TileLayer layer) {
        Tile tile = layer.tile();
        if (HighlightGuards.isHiddenOrHyperlane(scene, tile) || HighlightGuards.isTileImageMissing(tile)) {
            return;
        }
        Map<Player, Integer> pdsByOwner = pdsByOwner(scene.game(), tile);
        if (pdsByOwner.isEmpty()) {
            HighlightGuards.fogOut(layer);
            return;
        }
        int owners = pdsByOwner.size();
        float scale = owners >= 3 ? 6.0f / owners : 3.0f;
        int x = TILE_PADDING + layer.spiralOffsetX() + (int) ((345 - 73 * scale) / 2);
        int y = TILE_PADDING + layer.spiralOffsetY() + (int) ((300 - owners * 48 * scale) / 2);
        for (Map.Entry<Player, Integer> entry : pdsByOwner.entrySet()) {
            Player owner = entry.getKey();
            drawOwner(layer.g(), owner, entry.getValue(), scene.showsGeneric(owner), scale, x, y);
            y += (int) (48 * scale);
        }
    }

    private static Map<Player, Integer> pdsByOwner(Game game, Tile tile) {
        Map<Player, Integer> counts = new LinkedHashMap<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (Map.Entry<UnitKey, Integer> unit : holder.getUnits().entrySet()) {
                if (unit.getKey().unitType() != UnitType.Pds || unit.getValue() <= 0) {
                    continue;
                }
                game.getPlayerByColorID(unit.getKey().colorID())
                        .ifPresent(owner -> counts.merge(owner, unit.getValue(), Integer::sum));
            }
        }
        return counts;
    }

    private static void drawOwner(Graphics2D g, Player owner, int count, boolean generic, float scale, int x, int y) {
        boolean lightText =
                "_wht.png".equals(Mapper.getColor(owner.getColorID()).blackWhiteFileSuffix());
        g.setColor(lightText ? Color.WHITE : Color.BLACK);
        BufferedImage token =
                ImageHelper.readScaled(ImageResources.commandToken(Mapper.getControlID(owner.getColor())), scale);
        if (token != null) {
            PlayerTokens.drawControlToken(g, token, owner, x, y, generic, scale / 2);
        }
        OutlinedText.drawCentered(
                g,
                String.valueOf(count),
                new Rectangle(
                        Math.round(x + 6 * scale),
                        Math.round(y + 12 * scale),
                        Math.round(61 * scale / 2),
                        Math.round(24 * scale * 2 / 3)),
                labelFont(scale, 64));
        OutlinedText.drawCentered(
                g,
                "PDS",
                new Rectangle(Math.round(x + 73 * scale / 2), y, Math.round(73 * scale / 2), Math.round(48 * scale)),
                labelFont(scale, 32));
    }

    private static Font labelFont(float scale, int sizeAtFullScale) {
        return Fonts.ti(Math.max(8, Math.round(sizeAtFullScale * scale / 3.0f)));
    }
}
