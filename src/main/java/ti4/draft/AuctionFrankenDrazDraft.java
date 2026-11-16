package ti4.draft;

import ti4.game.Game;

public class AuctionFrankenDrazDraft extends FrankenDrazDraft {

    public AuctionFrankenDrazDraft(Game owner) {
        super(owner);
    }

    @Override
    public String getSaveString() {
        return "auction_frankendraz";
    }
}
