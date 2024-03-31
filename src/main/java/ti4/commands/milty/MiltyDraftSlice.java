package ti4.commands.milty;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

@Data
public class MiltyDraftSlice {

    private String name;
    private MiltyDraftTile left;
    private MiltyDraftTile equidistant;
    private MiltyDraftTile right;
    private MiltyDraftTile front;
    private MiltyDraftTile farFront;

    @JsonIgnore
    public List<MiltyDraftTile> getTiles() {
        return new ArrayList<>(List.of(left, front, right, equidistant, farFront));
    }

    @JsonIgnore
    public int getOptimalTotalValue() {
        return getOptimalRes() + getOptimalInf() + getOptimalFlex();
    }

    @JsonIgnore
    public int getOptimalRes() {
        return getTiles().stream().map(MiltyDraftTile::getMilty_res).reduce(0, (x, y) -> x + y);
    }

    @JsonIgnore
    public int getOptimalInf() {
        return getTiles().stream().map(MiltyDraftTile::getMilty_inf).reduce(0, (x, y) -> x + y);
    }

    @JsonIgnore
    public int getOptimalFlex() {
        return getTiles().stream().map(MiltyDraftTile::getMilty_flex).reduce(0, (x, y) -> x + y);
    }

    @JsonIgnore
    public int getTotalRes() {
        return getTiles().stream().map(MiltyDraftTile::getResources).reduce(0, (x, y) -> x + y);
    }

    @JsonIgnore
    public int getTotalInf() {
        return getTiles().stream().map(MiltyDraftTile::getInfluence).reduce(0, (x, y) -> x + y);
    }

    public String ttsString() {
        List<String> ls = getTiles().stream().map(tile -> tile.getTile().getTileID()).toList();
        return String.join(",", ls);
    }
}
