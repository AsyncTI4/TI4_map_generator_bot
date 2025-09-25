package ti4.service.draft.draftables;

public abstract class SinglePickDraftable extends FixedNumberDraftable {

    @Override
    public int getNumPicksPerPlayer() {
        return 1;
    }
}
