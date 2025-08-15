package ti4.service.milty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MiltyDraftSlice {

    private String name;
    private List<MiltyDraftTile> tiles;

    public void setTiles(List<MiltyDraftTile> tiles) {
        this.tiles = new ArrayList<>(tiles);
    }

    @JsonIgnore
    public int getOptimalTotalValue() {
        return getOptimalRes() + getOptimalInf() + getOptimalFlex();
    }

    @JsonIgnore
    public int getOptimalRes() {
        return tiles.stream().map(MiltyDraftTile::getMiltyRes).reduce(0, Integer::sum);
    }

    @JsonIgnore
    public int getOptimalInf() {
        return tiles.stream().map(MiltyDraftTile::getMiltyInf).reduce(0, Integer::sum);
    }

    @JsonIgnore
    public int getOptimalFlex() {
        return tiles.stream().map(MiltyDraftTile::getMiltyFlex).reduce(0, Integer::sum);
    }

    @JsonIgnore
    public int getTotalRes() {
        return tiles.stream().map(MiltyDraftTile::getResources).reduce(0, Integer::sum);
    }

    @JsonIgnore
    public int getTotalInf() {
        return tiles.stream().map(MiltyDraftTile::getInfluence).reduce(0, Integer::sum);
    }

    public String ttsString() {
        List<String> ls = tiles.stream().map(tile -> tile.getTile().getTileID()).toList();
        return String.join(",", ls);
    }
}
