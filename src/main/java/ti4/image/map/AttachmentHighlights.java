package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.image.draw.OutlinedText;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.tile.TileGeometry;

final class AttachmentHighlights implements OffBoardHighlights {

    @Override
    public int draw(Graphics2D g2, Game game, Player player, Point miscTile) {
        int offBoardHighlighting = 0;
        Map<String, String> attachFiles = new HashMap<>();
        Map<String, Integer> attachCount = new HashMap<>();
        for (String planet : player.getPlanets()) {
            if (game.getTileFromPlanet(planet) == null) {
                Planet planetReal = game.getPlanetsInfo().get(planet);
                List<String> attach = new ArrayList<>(planetReal.getAttachments());
                attach.removeAll(Collections.singleton(null));
                attach.removeAll(Collections.singleton(""));
                if (attach.isEmpty()) {
                    continue;
                }
                attachFiles.put(planet, ImageResources.general("misc_chevrons_basic.png"));
                if (attach.contains("attachment_tombofemphidia.png")) {
                    attachFiles.put(planet, ImageResources.general("misc_chevrons_toe.png"));
                }
                attachCount.put(planet, attach.size());
                offBoardHighlighting++;
            }
        }
        if (offBoardHighlighting >= 2) {
            for (Map.Entry<String, String> entry : attachFiles.entrySet()) {
                String planet = entry.getKey();
                BufferedImage bufferedImage = ImageHelper.read(entry.getValue());
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                        24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                g2.drawImage(
                        bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30,
                        miscTile.y
                                + (TileGeometry.TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                - 30
                                + (player.isSpeaker() ? 30 : 0),
                        null);
                if (attachCount.get(planet) > 1) {
                    g2.setColor(Color.WHITE);
                    g2.fillOval(
                            miscTile.x + (345 - 80) / 2 - 30,
                            miscTile.y + (TileGeometry.TILE_HEIGHT - 16) / 2 - 30 + (player.isSpeaker() ? 30 : 0),
                            80,
                            80);
                    g2.setColor(Color.BLACK);
                    g2.fillOval(
                            miscTile.x + (345 - 72) / 2 - 30,
                            miscTile.y + (TileGeometry.TILE_HEIGHT - 16) / 2 - 30 + (player.isSpeaker() ? 30 : 0) + 4,
                            72,
                            72);
                    g2.setColor(Color.WHITE);
                    OutlinedText.drawCentered(
                            g2,
                            "" + attachCount.get(planet),
                            new Rectangle(
                                    miscTile.x + (345 - 80) / 2 - 30,
                                    miscTile.y
                                            + (TileGeometry.TILE_HEIGHT - 16) / 2
                                            - 30
                                            + (player.isSpeaker() ? 30 : 0),
                                    80,
                                    80),
                            Fonts.ti(48));
                }
            }
        } else if (offBoardHighlighting == 1) {
            String planet = attachFiles.keySet().iterator().next();

            BufferedImage bufferedImage = ImageHelper.read(attachFiles.get(planet));
            bufferedImage = ImageHelper.scale(
                    bufferedImage, (float) Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
            g2.drawImage(
                    bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y
                            + (TileGeometry.TILE_HEIGHT - bufferedImage.getHeight()) / 2
                            + (player.isSpeaker() ? 30 : 0),
                    null);
            if (attachCount.get(planet) > 1) {
                g2.setColor(Color.WHITE);
                g2.fillOval(
                        miscTile.x + (345 - 80) / 2,
                        miscTile.y + (TileGeometry.TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0),
                        80,
                        80);
                g2.setColor(Color.BLACK);
                g2.fillOval(
                        miscTile.x + (345 - 72) / 2,
                        miscTile.y + (TileGeometry.TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0) + 4,
                        72,
                        72);
                g2.setColor(Color.WHITE);
                OutlinedText.drawCentered(
                        g2,
                        "" + attachCount.get(planet),
                        new Rectangle(
                                miscTile.x + (345 - 80) / 2,
                                miscTile.y + (TileGeometry.TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0),
                                80,
                                80),
                        Fonts.ti(48));
            }
        }
        return offBoardHighlighting;
    }
}
