package ti4.model;

import ti4.draft.DraftItem;

public class DraftErrataModel implements ModelInterface{
    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public String getAlias() {
        return ItemCategory.toString()+":"+ItemId;
    }
    // The type of item to be drafted
    public DraftItem.Category ItemCategory;

    // The system ID of the item. Only convert this to player-readable text when necessary
    public String ItemId;

    public DraftErrataModel[] AdditionalComponents;
    public DraftErrataModel[] OptionalSwaps;
    public boolean Undraftable;

    public boolean AlwaysAddToPool;
}
