package ti4.ai.expedition;

import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;

public record Holdings(Wallet wallet, int actionCards) {

    public static Holdings of(Game game, Player seat) {
        return new Holdings(Wallet.of(game, seat), seat.getAcCount());
    }

    public Holdings withWallet(Wallet adjusted) {
        return new Holdings(adjusted, actionCards);
    }

    public Holdings withActionCards(int count) {
        return new Holdings(wallet, count);
    }
}
