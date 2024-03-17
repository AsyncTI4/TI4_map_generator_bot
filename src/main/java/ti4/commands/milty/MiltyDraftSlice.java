package ti4.commands.milty;

public class MiltyDraftSlice {

    private String name;

    private MiltyDraftTile left;
    private MiltyDraftTile equadistant;
    private MiltyDraftTile right;
    private MiltyDraftTile front;
    private MiltyDraftTile farFront;

    public Integer getOptimalTotalValue() {
        int total = 0;
        total += left.getMilty_influence() + left.getMilty_resources();
        total += equadistant.getMilty_influence() + equadistant.getMilty_resources();
        total += right.getMilty_influence() + right.getMilty_resources();
        total += front.getMilty_influence() + front.getMilty_resources();
        total += farFront.getMilty_influence() + farFront.getMilty_resources();
        return total;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MiltyDraftTile getLeft() {
        return left;
    }

    public void setLeft(MiltyDraftTile left) {
        this.left = left;
    }

    public MiltyDraftTile getEquadistant() {
        return equadistant;
    }

    public void setEquadistant(MiltyDraftTile equadistant) {
        this.equadistant = equadistant;
    }

    public MiltyDraftTile getRight() {
        return right;
    }

    public void setRight(MiltyDraftTile right) {
        this.right = right;
    }

    public MiltyDraftTile getFront() {
        return front;
    }

    public void setFront(MiltyDraftTile front) {
        this.front = front;
    }

    public MiltyDraftTile getFarFront() {
        return farFront;
    }

    public void setFarFront(MiltyDraftTile farFront) {
        this.farFront = farFront;
    }
}
