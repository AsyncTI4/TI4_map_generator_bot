package ti4.generator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GenerateMap {

    private static Logger logger = Logger.getLogger(GenerateMap.class.getName());


    private File setupFile;
    private Graphics graphics;
    private BufferedImage mainImage;

    public GenerateMap(File setupFile) {
        this.setupFile = setupFile;
        BufferedImage setupImage = null; //image
        try {
            setupImage = ImageIO.read(setupFile);
        } catch (IOException e) {

            logger.log(Level.SEVERE, "Could read file data for setup file", e);

        }
        if (setupImage == null) {
            logger.log(Level.SEVERE, "Could not init map generator");
            //todo message to user
        }
        mainImage = new BufferedImage(setupImage.getWidth(), setupImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        graphics = mainImage.getGraphics();
//        graphics.drawImage(mainImage, 0, 0, null);
    }

    public File saveImage() {
        File file = new File("E:\\DEV TI4\\temp\\temp.png");
        try {
            ImageIO.write(mainImage, "PNG", file);
            //todo fix temp directory
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not save generated map");
        }
        return file;
    }

    public void addTile(File tile, String position) {
        try {
            BufferedImage image = ImageIO.read(tile);
            Point positionPoint = PositionMapper.getPosition(position);
            graphics.drawImage(image, positionPoint.x, positionPoint.y, null);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could read file data for tile", e);
        }

    }

//    public File generateMap() {
//        BufferedImage a = ImageIO.read(new File(imageList.get(0))); //imageList holds the path to all images
//        BufferedImage b = ImageIO.read(new File(imageList.get(1)));
//        BufferedImage c = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_ARGB);
//        Graphics g = c.getGraphics();
//        g.drawImage(a, 0, 0, null);
//        g.drawImage(b, 0, 0, null);
//        ImageIO.write(c, "PNG", new File(combainedImages));
//    }
}
