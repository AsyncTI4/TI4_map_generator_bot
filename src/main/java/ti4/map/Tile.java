package ti4.map;

public class Tile {
    private String tileID;
    private String position;

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
}
