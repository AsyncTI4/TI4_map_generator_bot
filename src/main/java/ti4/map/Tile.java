package ti4.map;

import ti4.ResourceHelper;
import ti4.generator.TilesMapper;
import ti4.helpers.LoggerHandler;
import ti4.message.MessageHelper;

public class Tile {
    private String tileID;
    private String position;
    private String tilePath;

    public Tile(String tileID, String position) {
        this.tileID = tileID;
        this.position = position;
    }

    public String getTileID() {
        return tileID;
    }

    public String getPosition() {
        return position;
    }

    public String getTilePath() {
        if (tilePath != null)
        {
            return tilePath;
        }
        String tileName = TilesMapper.getTileName(tileID);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null)
        {
            LoggerHandler.log("Could not find tile: " + tileID);
        }
        return tilePath;
    }
}
