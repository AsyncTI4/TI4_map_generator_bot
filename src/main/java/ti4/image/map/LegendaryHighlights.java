package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.tile.TileGeometry;
import ti4.model.Mapper;
import ti4.model.PlanetModel;

final class LegendaryHighlights implements OffBoardHighlights {

    @Override
    public int draw(Graphics2D g2, Game game, Player player, Point miscTile) {
        int offBoardHighlighting = 0;
        boolean hasNanoForge = player.hasRelic("nanoforge") || player.hasRelic("absol_nanoforge");
        for (String planet : player.getPlanets()) {
            PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
            offBoardHighlighting +=
                    (custodiaVigilia.getLegendaryAbilityName() != null && game.getTileFromPlanet(planet) == null)
                            ? 1
                            : 0;
        }
        if (offBoardHighlighting >= 1) {
            String legendaryFile = ImageResources.general("Legendary_complete.png");
            BufferedImage bufferedImage = ImageHelper.read(legendaryFile);
            if (offBoardHighlighting + (hasNanoForge ? 1 : 0) >= 2) {
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24_000.0f
                        / (offBoardHighlighting + (hasNanoForge ? 1 : 0))
                        / bufferedImage.getWidth()
                        / bufferedImage.getHeight()));
                for (int i = 0; i < offBoardHighlighting; i++) {
                    g2.drawImage(
                            bufferedImage,
                            miscTile.x
                                    + (TileGeometry.TILE_WIDTH - bufferedImage.getWidth()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1),
                            miscTile.y
                                    + (TileGeometry.TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1)
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            } else {
                g2.drawImage(
                        bufferedImage,
                        miscTile.x + (TileGeometry.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                        miscTile.y
                                + (TileGeometry.TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                + (player.isSpeaker() ? 30 : 0),
                        null);
            }
        }
        if (hasNanoForge) {
            String relicFile = ImageResources.general("Relic.png");
            BufferedImage bufferedImage = ImageHelper.read(relicFile);
            if (offBoardHighlighting >= 1) {
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                        24_000.0f / (offBoardHighlighting + 1) / bufferedImage.getWidth() / bufferedImage.getHeight()));
                g2.drawImage(
                        bufferedImage,
                        miscTile.x + (TileGeometry.TILE_WIDTH - bufferedImage.getWidth()) / 2 + 30,
                        miscTile.y
                                + (TileGeometry.TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                + 30
                                + (player.isSpeaker() ? 30 : 0),
                        null);
            } else {
                g2.drawImage(
                        bufferedImage,
                        miscTile.x + (TileGeometry.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                        miscTile.y
                                + (TileGeometry.TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                + (player.isSpeaker() ? 30 : 0),
                        null);
            }
            offBoardHighlighting++;
        }
        return offBoardHighlighting;
    }
}
