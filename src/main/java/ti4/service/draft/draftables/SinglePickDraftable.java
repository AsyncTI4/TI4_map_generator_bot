package ti4.service.draft.draftables;

public abstract class SinglePickDraftable extends MultiPickDraftable {

    @Override
    public int getNumPicksPerPlayer() {
        return 1;
    }
}
