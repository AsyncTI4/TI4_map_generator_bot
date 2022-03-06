package ti4.generator;

import ti4.ResourceHelper;
import ti4.helpers.Constants;
import ti4.helpers.LoggerHandler;
import ti4.helpers.Storage;
import ti4.map.Map;
import ti4.map.Tile;
import ti4.map.UnitHolder;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class GenerateMap {
    private Graphics graphics;
    private BufferedImage mainImage;
    private int width;
    private int height;

    private static GenerateMap instance;

    private GenerateMap() {
        String tileFile = ResourceHelper.getInstance().getTileFile("6player_setup.png");
        File setupFile = new File(tileFile);
        BufferedImage setupImage = null;
        try {
            setupImage = ImageIO.read(setupFile);
        } catch (IOException e) {
            LoggerHandler.logError("Could read file data for setup file", e);
        }
        if (setupImage == null) {
            LoggerHandler.log("Could not init map generator");
            //todo message to user
        }
        width = setupImage.getWidth();
        height = setupImage.getHeight();
        resetImage();
    }

    private void resetImage() {
        mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        graphics = mainImage.getGraphics();
    }

    public static GenerateMap getInstance() {
        if (instance == null) {
            instance = new GenerateMap();
        }
        return instance;
    }

    public File saveImage(Map map) {
        resetImage();
        //todo fix temp map name
        File file = Storage.getMapImageStorage("temp.png");
        try {
            HashMap<String, Tile> tileMap = new HashMap<>(map.getTileMap());
            String setup = tileMap.keySet().stream()
                    .filter(key -> key.startsWith("setup"))
                    .findFirst()
                    .orElse(null);
            if (setup != null) {
                addTile(tileMap.get(setup));
                tileMap.remove(setup);
            }
            tileMap.keySet().stream()
                    .sorted()
                    .forEach(key -> addTile(tileMap.get(key)));
            ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("png").next();
            imageWriter.setOutput(ImageIO.createImageOutputStream(file));
            ImageWriteParam defaultWriteParam = imageWriter.getDefaultWriteParam();
            if (defaultWriteParam.canWriteCompressed()) {
                defaultWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                defaultWriteParam.setCompressionQuality(0.01f);
            }

            imageWriter.write(null, new IIOImage(mainImage, null, null), defaultWriteParam);


//            ImageIO.write(mainImage, "PNG", file);
        } catch (IOException e) {
            LoggerHandler.log("Could not save generated map");
        }
        return file;
    }

    private void addTile(Tile tile) {
        try {
            BufferedImage image = ImageIO.read(new File(tile.getTilePath()));
            Point positionPoint = PositionMapper.getTilePosition(tile.getPosition());
            int tileX = positionPoint.x;
            int tileY = positionPoint.y;
            graphics.drawImage(image, tileX, tileY, null);

            ArrayList<Rectangle> rectangles = new ArrayList<>();

            Collection<UnitHolder> unitHolders = tile.getUnitHolders().values();
            int degree;
            int degreeChange = 10;
            for (UnitHolder unitHolder : unitHolders) {
                degree = 0;
                int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS : Constants.RADIUS;
                HashMap<String, Integer> units = unitHolder.getUnits();
                for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
                    String unitID = unitEntry.getKey();
                    Integer unitCount = unitEntry.getValue();
                    image = ImageIO.read(new File(tile.getUnitPath(unitID)));
                    Point centerPosition = unitHolder.getHolderCenterPosition();
                    for (int i = 0; i < unitCount; i++) {
                        boolean searchPosition = true;
                        int x = 0;
                        int y = 0;
                        while (searchPosition) {
                            x = (int) (radius * Math.sin(degree));
                            y = (int) (radius * Math.cos(degree));

                            int possibleX = tileX + centerPosition.x + x - (image.getWidth() / 2);
                            int possibleY = tileY + centerPosition.y + y - (image.getHeight() / 2);

                            graphics.setColor(Color.CYAN);
                            graphics.drawOval(tileX + centerPosition.x +x, tileY + centerPosition.y+y, 5, 5);

                            BufferedImage finalImage = image;
                            if (rectangles.stream().noneMatch(rectangle -> rectangle.intersects(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()))) {
                                searchPosition = false;
                            } else if (degree > 360) {
                                searchPosition = false;
                            }
                            degree += degreeChange;
                            if (!searchPosition)
                            {
                                rectangles.add(new Rectangle(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()));
                            }
                        }
                        graphics.drawImage(image, tileX + centerPosition.x + x - (image.getWidth() / 2), tileY + centerPosition.y + y - (image.getHeight() / 2), null);

                        graphics.setColor(Color.CYAN);
                        graphics.drawOval(tileX + centerPosition.x, tileY + centerPosition.y, 5, 5);
                        graphics.setColor(Color.magenta);
                        graphics.drawOval(tileX + centerPosition.x + x, tileY + centerPosition.y + y, 5, 5);
                    }
                }
            }

        } catch (
                IOException e) {
            LoggerHandler.log("Error drawing tile: " + tile.getTileID(), e);
        }
    }
}
