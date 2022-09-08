package ti4.commands.milty;

public class MiltyDraftSlice {

    private String name;

    private MiltyDraftTile left;
    private MiltyDraftTile equadistant;
    private MiltyDraftTile right;
    private MiltyDraftTile front;
    private MiltyDraftTile farFront;

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
