package ti4.ai.expedition;

import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;

public record Holdings(Wallet wallet, int actionCards, boolean passing, boolean racing) {

    public static Holdings of(Game game, Player seat) {
        return new Holdings(Wallet.of(game, seat), seat.getAcCount(), false, false);
    }

    public Holdings withWallet(Wallet adjusted) {
        return new Holdings(adjusted, actionCards, passing, racing);
    }

    public Holdings withActionCards(int count) {
        return new Holdings(wallet, count, passing, racing);
    }

    public Holdings racingForTheSlice(boolean racing) {
        return new Holdings(wallet, actionCards, passing, racing);
    }

    public Holdings atPassing() {
        return new Holdings(wallet, actionCards, true, racing);
    }
}
