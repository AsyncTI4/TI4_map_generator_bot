package ti4.image;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.logging.BotLogger;
import ti4.model.PlanetModel;

final class TraitHighlights implements OffBoardHighlights {

    @Override
    public int draw(Graphics2D g2, Game game, Player player, Point miscTile) {
        int offBoardHighlighting = 0;
        List<String> traitFiles = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
            if (game.getTileFromPlanet(planet) == null) {
                Planet planetReal = game.getPlanetsInfo().get(planet);
                String traitFile = "";
                List<String> traits = planetReal.getPlanetType();

                if ("faction".equalsIgnoreCase(planetReal.getOriginalPlanetType()) && traits.isEmpty()) {
                    if (custodiaVigilia.getFactionHomeworld() == null) {
                        traitFile = ImageResources.general("Legendary_complete.png");
                    } else {
                        traitFile = ResourceHelper.getInstance()
                                .getFactionFile(custodiaVigilia.getFactionHomeworld() + ".png");
                    }
                } else if (traits.size() == 1) {
                    String t = planetReal.getPlanetType().getFirst();
                    traitFile = ResourceHelper.getInstance()
                            .getGeneralFile(("" + t.charAt(0)).toUpperCase()
                                    + t.substring(1).toLowerCase() + ".png");
                } else if (!traits.isEmpty()) {
                    String t = "";
                    t += traits.contains("cultural") ? "C" : "";
                    t += traits.contains("hazardous") ? "H" : "";
                    t += traits.contains("industrial") ? "I" : "";
                    if ("CHI".equals(t)) {
                        traitFile = ImageResources.planetCard("pc_attribute_combo_CHI_big.png");
                    } else {
                        traitFile = ImageResources.planetCard("pc_attribute_combo_" + t + ".png");
                    }
                }
                traitFiles.add(traitFile);
                offBoardHighlighting++;
            }
        }
        if (offBoardHighlighting >= 2) {
            for (int i = 0; i < offBoardHighlighting; i++) {
                BufferedImage bufferedImage = ImageHelper.read(traitFiles.get(i));
                if (bufferedImage == null) {
                    BotLogger.error("Could not load trait file " + traitFiles.get(i));
                    continue;
                }
                bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                        24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                g2.drawImage(
                        bufferedImage,
                        miscTile.x
                                + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2
                                - 30
                                + i * 60 / (offBoardHighlighting - 1),
                        miscTile.y
                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                - 30
                                + i * 60 / (offBoardHighlighting - 1)
                                + (player.isSpeaker() ? 30 : 0),
                        null);
            }
        } else if (offBoardHighlighting == 1) {
            BufferedImage bufferedImage = ImageHelper.read(traitFiles.getFirst());
            if (bufferedImage != null) {
                bufferedImage = ImageHelper.scale(bufferedImage, (float)
                        Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                g2.drawImage(
                        bufferedImage,
                        miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                        miscTile.y
                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                + (player.isSpeaker() ? 30 : 0),
                        null);
            }
        }
        return offBoardHighlighting;
    }
}
