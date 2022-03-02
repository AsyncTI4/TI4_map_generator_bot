package ti4.generator;

import ti4.helpers.LoggerHandler;
import ti4.map.Storage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GenerateMap {
    private Graphics graphics;
    private BufferedImage mainImage;

    public GenerateMap(File setupFile_) {
        File setupFile = setupFile_;
        BufferedImage setupImage = null;
        try {
            setupImage = ImageIO.read(setupFile);
        } catch (IOException e) {
            LoggerHandler.logError("Could read file data for setup file", e);
        }
        if (setupImage == null) {
            LoggerHandler.log( "Could not init map generator");
            //todo message to user
        }
        mainImage = new BufferedImage(setupImage.getWidth(), setupImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        graphics = mainImage.getGraphics();
    }

    public File saveImage() {
        //todo fix temp map name
        File file = Storage.getMapStorage("temp.png");
        try {
            ImageIO.write(mainImage, "PNG", file);
        } catch (IOException e) {
            LoggerHandler.log("Could not save generated map");
        }
        return file;
    }

    public void addTile(File tile, String position) {
        try {
            BufferedImage image = ImageIO.read(tile);
            Point positionPoint = PositionMapper.getPosition(position);
            graphics.drawImage(image, positionPoint.x, positionPoint.y, null);
        } catch (IOException e) {
            LoggerHandler.log("Could read file data for tile", e);
        }
    }
}
