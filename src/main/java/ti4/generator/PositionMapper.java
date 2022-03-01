package ti4.generator;

import ti4.ResourceHelper;

import javax.annotation.CheckForNull;
import java.awt.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

//Handles positions of map
public class PositionMapper {

    private static Logger logger = Logger.getLogger(PositionMapper.class.getName());
    private static Properties positionMap = new Properties();

    public static void init()
    {
        String positionFile = new ResourceHelper().getPositionFile("6player.properties");
        if (positionFile != null) {
            try (InputStream input = new FileInputStream(positionFile)) {
                positionMap.load(input);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not read position file", e);
            }
        }
    }

    @CheckForNull
    public static Point getPosition(String position) {
        String value = positionMap.getProperty(position);
        if (value != null) {
            StringTokenizer tokenizer = new StringTokenizer(value, ",");
            try {
                int x = Integer.parseInt(tokenizer.nextToken());
                int y = Integer.parseInt(tokenizer.nextToken());
                return new Point(x, y);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not parse position coordinates", e);
            }
        }
        return null;
    }
}
