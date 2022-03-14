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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class GenerateMap {
    private Graphics graphics;
    private BufferedImage mainImage;
    private int width;
    private int height;
    private static Point tilePositionPoint = new Point(230, 295);
    private static Point numberPositionPoint = new Point(45, 35);

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
        } catch (IOException e) {
            LoggerHandler.log("Could not save generated map");
        }
        String absolutePath = file.getAbsolutePath().replace(".png", ".jpg");
        try (FileInputStream fileInputStream = new FileInputStream(file);
             FileOutputStream fileOutputStream = new FileOutputStream(absolutePath)) {

            final BufferedImage image = ImageIO.read(fileInputStream);
            fileInputStream.close();

            final BufferedImage convertedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            convertedImage.createGraphics().drawImage(image, 0, 0, Color.black, null);

            final boolean canWrite = ImageIO.write(convertedImage, "jpg", fileOutputStream);

            if (!canWrite) {
                throw new IllegalStateException("Failed to write image.");
            }
        } catch (IOException e) {
            LoggerHandler.log("Could not save jpg file", e);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        return new File(absolutePath);
    }

    private void addTile(Tile tile) {
        try {
            BufferedImage image = ImageIO.read(new File(tile.getTilePath()));
            Point positionPoint = PositionMapper.getTilePosition(tile.getPosition());
            int tileX = positionPoint.x;
            int tileY = positionPoint.y;
            graphics.drawImage(image, tileX, tileY, null);

            graphics.setFont(Storage.getFont20());
            graphics.setColor(Color.WHITE);
            graphics.drawString(tile.getPosition(), tileX + tilePositionPoint.x, tileY + tilePositionPoint.y);

            ArrayList<Rectangle> rectangles = new ArrayList<>();

            Collection<UnitHolder> unitHolders = new ArrayList<>(tile.getUnitHolders().values());
            UnitHolder spaceUnitHolder = unitHolders.stream().filter(unitHolder -> unitHolder.getName().equals(Constants.SPACE)).findFirst().orElse(null);
            if (spaceUnitHolder != null) {
                image = addCC(tile, image, tileX, tileY, spaceUnitHolder);
                unitHolders.remove(spaceUnitHolder);
                unitHolders.add(spaceUnitHolder);
            }
            int degree;
            int degreeChange = 5;
            for (UnitHolder unitHolder : unitHolders) {
                degree = 180;
                image = addControl(tile, image, tileX, tileY, unitHolder);
                image = addToken(tile, image, tileX, tileY, unitHolder);
                int radius = unitHolder.getName().equals(Constants.SPACE) ? Constants.SPACE_RADIUS : Constants.RADIUS;
                image = addUnits(tile, image, tileX, tileY, rectangles, degree, degreeChange, unitHolder, radius);
            }

        } catch (IOException e) {
            LoggerHandler.log("Error drawing tile: " + tile.getTileID(), e);
        }
    }
    private BufferedImage resizeImage(BufferedImage originalImage, float percent) throws IOException {
        int scaledWidth = (int)(originalImage.getWidth() * percent);
        int scaledHeight = (int)(originalImage.getHeight() * percent);
        Image resultingImage = originalImage.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_DEFAULT);
        BufferedImage outputImage = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    private BufferedImage addCC(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder) {
        HashSet<String> ccList = unitHolder.getCCList();
        int deltaX = 0;//ccList.size() * 20;
        int deltaY = 0;//ccList.size() * 20;
        for (String ccID : ccList) {
            String ccPath = tile.getCCPath(ccID);
            if (ccPath == null) {
                LoggerHandler.log("Could not parse cc file for: " + ccID);
                continue;
            }
            try {
                image = resizeImage(ImageIO.read(new File(ccPath)), 0.85f);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse cc file for: " + ccID, e);
            }
            Point centerPosition = unitHolder.getHolderCenterPosition();
            graphics.drawImage(image, tileX + 10 + deltaX, tileY + centerPosition.y - 40 + deltaY, null);
            deltaX += image.getWidth()/5;
            deltaY += image.getHeight()/4;
        }
        return image;
    }

    private BufferedImage addControl(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder) {
        HashSet<String> controlList = unitHolder.getControlList();
        for (String controlID : controlList) {
            String controlPath = tile.getCCPath(controlID);
            if (controlPath == null) {
                LoggerHandler.log("Could not parse control token file for: " + controlID);
                continue;
            }
            try {
                image = resizeImage(ImageIO.read(new File(controlPath)), 0.85f);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse control token file for: " + controlID, e);
            }
            Point centerPosition = unitHolder.getHolderCenterPosition();
            if (unitHolder.getTokenList().isEmpty()) {
                graphics.drawImage(image, tileX + centerPosition.x - (image.getWidth() / 2), tileY + centerPosition.y - (image.getHeight() / 2), null);
            } else {
                graphics.drawImage(image, tileX + centerPosition.x + (image.getWidth() / 2), tileY + centerPosition.y + (image.getHeight() / 2), null);
            }
        }
        return image;
    }

    private BufferedImage addToken(Tile tile, BufferedImage image, int tileX, int tileY, UnitHolder unitHolder) {
        HashSet<String> tokenList = unitHolder.getTokenList();
        int deltaY = 0;
        int deltaYFirst = 0;
        boolean first = tokenList.size() > 1;
        for (String tokenID : tokenList) {
            String tokenPath = tile.getTokenPath(tokenID);
            if (tokenPath == null) {
                LoggerHandler.log("Could not parse token file for: " + tokenID);
                continue;
            }
            try {
                image = resizeImage(ImageIO.read(new File(tokenPath)), 0.85f);
                if (first){
                    first = false;
                    deltaYFirst = image.getHeight();
                }
            } catch (Exception e) {
                LoggerHandler.log("Could not parse control token file for: " + tokenID, e);
            }
            Point centerPosition = unitHolder.getHolderCenterPosition();
            graphics.drawImage(image, tileX + centerPosition.x - (image.getWidth() / 2), tileY + centerPosition.y - deltaYFirst + deltaY - (image.getHeight() / 2), null);
            deltaY += image.getHeight();
        }
        return image;
    }

    private BufferedImage addUnits(Tile tile, BufferedImage image, int tileX, int tileY, ArrayList<Rectangle> rectangles, int degree, int degreeChange, UnitHolder unitHolder, int radius) {
        HashMap<String, Integer> units = unitHolder.getUnits();
        for (java.util.Map.Entry<String, Integer> unitEntry : units.entrySet()) {
            String unitID = unitEntry.getKey();
            Integer unitCount = unitEntry.getValue();

            Color groupUnitColor = Color.WHITE;
            Integer bulkUnitCount = null;
            if (unitID.startsWith("ylw")) {
                groupUnitColor = Color.BLACK;
            }
            if (unitID.endsWith(Constants.COLOR_FF)) {
                unitID = unitID.replace(Constants.COLOR_FF, Constants.BULK_FF);
                bulkUnitCount = unitCount;
            } else if (unitID.endsWith(Constants.COLOR_GF)) {
                unitID = unitID.replace(Constants.COLOR_GF, Constants.BULK_GF);
                bulkUnitCount = unitCount;
            }

            float scaleOfUnit = 0.9f;
            try {
                image = resizeImage(ImageIO.read(new File(tile.getUnitPath(unitID))), scaleOfUnit);
            } catch (Exception e) {
                LoggerHandler.log("Could not parse unit file for: " + unitID, e);
            }
            if (bulkUnitCount != null && bulkUnitCount > 0) {
                unitCount = 1;
            }

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
                    BufferedImage finalImage = image;
                    if (rectangles.stream().noneMatch(rectangle -> rectangle.intersects(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()))) {
                        searchPosition = false;
                    } else if (degree > 360) {
                        searchPosition = false;
                        degree += 3;//To change degree if we did not find place, might be better placement then
                    }
                    degree += degreeChange;
                    if (!searchPosition) {
                        rectangles.add(new Rectangle(possibleX, possibleY, finalImage.getWidth(), finalImage.getHeight()));
                    }
                }
                int imageX = tileX + centerPosition.x + x - (image.getWidth() / 2);
                int imageY = tileY + centerPosition.y + y - (image.getHeight() / 2);
                graphics.drawImage(image, imageX, imageY, null);
                if (bulkUnitCount != null) {
                    graphics.setFont(Storage.getFont26());
                    graphics.setColor(groupUnitColor);
                    int scaledNumberPositionX = (int)(numberPositionPoint.x * scaleOfUnit);
                    int scaledNumberPositionY = (int)(numberPositionPoint.y  * scaleOfUnit);
                    graphics.drawString(Integer.toString(bulkUnitCount), imageX + scaledNumberPositionX, imageY + scaledNumberPositionY);
                }

                //Center of planets and tile marker
//                graphics.setColor(Color.CYAN);
//                graphics.drawLine(tileX+centerPosition.x-5, tileY+centerPosition.y, tileX+centerPosition.x+5, tileY+centerPosition.y);
//                graphics.drawLine(tileX+centerPosition.x, tileY+centerPosition.y-5, tileX+centerPosition.x, tileY+centerPosition.y+5);
            }
        }
        return image;
    }
}
