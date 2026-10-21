package ti4.image;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

final class TechSkipHighlights implements OffBoardHighlights {

    @Override
    public int draw(Graphics2D g2, Game game, Player player, Point miscTile) {
        int offBoardHighlighting = 0;
        List<String> techFiles = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            if (game.getTileFromPlanet(planet) == null) {
                Planet planetReal = game.getPlanetsInfo().get(planet);
                List<String> skips = planetReal.getTechSpeciality();
                skips.removeAll(Collections.singleton(null));
                skips.removeAll(Collections.singleton(""));
                if (skips.isEmpty()) {
                    continue;
                }
                for (String skip : skips) {
                    switch (skip.toLowerCase()) {
                        case "biotic":
                            techFiles.add(ResourceHelper.getInstance().getGeneralFile("Biotic light.png"));
                            break;
                        case "cybernetic":
                            techFiles.add(ResourceHelper.getInstance().getGeneralFile("Cybernetic light.png"));
                            break;
                        case "propulsion":
                            techFiles.add(ResourceHelper.getInstance().getGeneralFile("Propulsion_light.png"));
                            break;
                        case "warfare":
                            techFiles.add(ResourceHelper.getInstance().getGeneralFile("Warfare_light.png"));
                            break;
                        default:
                            techFiles.add(ResourceHelper.getInstance().getGeneralFile("Generic_Technology.png"));
                    }
                    offBoardHighlighting++;
                }
            }
        }
        if (offBoardHighlighting >= 2) {
            for (int i = 0; i < offBoardHighlighting; i++) {
                BufferedImage bufferedImage = ImageHelper.read(techFiles.get(i));
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                        24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                g2.drawImage(
                        bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30 + i * 60 / (offBoardHighlighting - 1),
                        miscTile.y
                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                - 30
                                + i * 60 / (offBoardHighlighting - 1)
                                + (player.isSpeaker() ? 30 : 0),
                        null);
            }
        } else if (offBoardHighlighting == 1) {
            BufferedImage bufferedImage = ImageHelper.read(techFiles.getFirst());
            bufferedImage = ImageHelper.scale(
                    bufferedImage, (float) Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
            g2.drawImage(
                    bufferedImage,
                    miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                    miscTile.y
                            + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                            + (player.isSpeaker() ? 30 : 0),
                    null);
        }
        return offBoardHighlighting;
    }
}
