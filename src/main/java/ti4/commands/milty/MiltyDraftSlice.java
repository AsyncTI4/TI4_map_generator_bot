package ti4.commands.milty;

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
    public int getOptimalTotalValue() {
        int total = 0;
        total += left.getMilty_influence() + left.getMilty_resources();
        total += equidistant.getMilty_influence() + equidistant.getMilty_resources();
        total += right.getMilty_influence() + right.getMilty_resources();
        total += front.getMilty_influence() + front.getMilty_resources();
        total += farFront.getMilty_influence() + farFront.getMilty_resources();
        return total;
    }
    
    @JsonIgnore
    public int getOptimalInf() {
        int total = 0;
        total += left.getMilty_influence() + left.getMilty_resources();
        total += equidistant.getMilty_influence() + equidistant.getMilty_resources();
        total += right.getMilty_influence() + right.getMilty_resources();
        total += front.getMilty_influence() + front.getMilty_resources();
        total += farFront.getMilty_influence() + farFront.getMilty_resources();
        return total;
    }
    
    @JsonIgnore
    public int getOptimalRes() {
        int total = 0;
        total += left.getMilty_influence() + left.getMilty_resources();
        total += equidistant.getMilty_influence() + equidistant.getMilty_resources();
        total += right.getMilty_influence() + right.getMilty_resources();
        total += front.getMilty_influence() + front.getMilty_resources();
        total += farFront.getMilty_influence() + farFront.getMilty_resources();
        return total;
    }

    @JsonIgnore
    public int getFlex() {
        int total = 0;
        total += left.getMilty_influence() + left.getMilty_resources();
        total += equidistant.getMilty_influence() + equidistant.getMilty_resources();
        total += right.getMilty_influence() + right.getMilty_resources();
        total += front.getMilty_influence() + front.getMilty_resources();
        total += farFront.getMilty_influence() + farFront.getMilty_resources();
        return total;
    }

    public String ttsString() {
        List<String> ls = List.of(left, front, right, equidistant, farFront).stream().map(tile -> tile.getTile().getTileID()).toList();
        return String.join(",", ls);
    }
}
