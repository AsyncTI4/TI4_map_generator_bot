package ti4.generator;

import ti4.ResourceHelper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TilesMapper {
    private static final Logger logger = Logger.getLogger(TilesMapper.class.getName());
    private static final Properties tilesMap = new Properties();

    public static void init() {
        String positionFile = ResourceHelper.getInstance().getInfoFile("tiles.properties");
        if (positionFile != null) {
            try (InputStream input = new FileInputStream(positionFile)) {
                tilesMap.load(input);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not read tiles name file", e);
            }
        }
    }

    public static String getTileName(String tileID) {
        return tilesMap.getProperty(tileID);
    }
}
