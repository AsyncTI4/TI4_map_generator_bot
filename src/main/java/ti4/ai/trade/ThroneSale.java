package ti4.ai.trade;

import java.util.Optional;
import java.util.OptionalInt;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.eval.GameHorizon;
import ti4.ai.promissory.NoteGiving;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ScoringPaths;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.scoring.WinRace;
import ti4.ai.scoring.WinRace.Boost;
import ti4.ai.secrets.ActionSecretOdds;
import ti4.ai.secrets.SecretOdds;
import ti4.ai.secrets.SecretPhase;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class ThroneSale {

    static final double MIN_PRICE = 10;
    private static final int THRONE_POINT = 1;
    private static final int STYX_POINT = 1;
    private static final int MECATOL_POINT = 1;

    static boolean allowed(Game game, Player seat, Player partner, Deal deal) {
        if (!givesOwnThrone(game, seat, deal)) return true;
        if (publicPathToWin(game, partner)) return false;
        if (Extortion.pointsSaved(game, seat, partner, deal) > 0) return true;
        boolean swap = swapsThrones(game, seat, partner, deal);
        int savedPoints = savedPoints(game, seat, deal);
        if (hiddenPathToWin(game, seat, partner) && !winsFirst(game, seat, partner, swap, savedPoints)) return false;
        return swap || savedPoints > 0 || pricedHighEnough(game, seat, partner, deal);
    }

    static boolean givesOwnThrone(Game game, Player seat, Deal deal) {
        return deal.items().stream()
                .filter(item -> item.isFrom(seat.getFaction()))
                .map(item -> NotesForTrade.aliasOf(game, item))
                .flatMap(Optional::stream)
                .anyMatch(alias -> alias.endsWith(NoteGiving.THRONE) && Standings.same(game.getPNOwner(alias), seat));
    }

    static boolean publicPathToWin(Game game, Player receiver) {
        return receiver.getTotalVictoryPoints() + THRONE_POINT + ScoringPaths.readyPoints(game, receiver)
                >= GameHorizon.goal(game);
    }

    static boolean hiddenPathToWin(Game game, Player seat, Player receiver) {
        double reach = ScoringPaths.readyPoints(game, receiver)
                + (ScoringPaths.imperialAwaitsMecatol(game, receiver) ? MECATOL_POINT : 0)
                + SecretOdds.chanceReady(game, seat, receiver)
                + styx(game, receiver);
        return receiver.getTotalVictoryPoints() + THRONE_POINT + reach >= GameHorizon.goal(game);
    }

    static boolean winsFirst(Game game, Player seat, Player partner, boolean swap, int savedPoints) {
        Boost own = new Boost(swap ? THRONE_POINT : 0, ownActionPoints(game, seat), savedPoints, false);
        OptionalInt mine = WinRace.finish(game, seat, own);
        if (!WinRace.finishesBefore(
                mine, WinRace.finish(game, partner, WinRace.feared(game, seat, partner, THRONE_POINT)))) {
            return false;
        }
        return Standings.others(game, seat).stream()
                .filter(other -> other != partner)
                .allMatch(other -> WinRace.finishesBefore(
                        mine, WinRace.finish(game, other, WinRace.feared(game, seat, other, 0))));
    }

    private static int ownActionPoints(Game game, Player seat) {
        double secrets = seat.getSecretsUnscored().keySet().stream()
                .filter(id -> SecretPhase.of(id) == SecretPhase.ACTION)
                .mapToDouble(id -> ActionSecretOdds.perRound(game, seat, id))
                .sum();
        return (int) Math.floor(secrets) + styx(game, seat);
    }

    private static int styx(Game game, Player player) {
        return FractureCampaign.dock(game, player).isPresent() ? STYX_POINT : 0;
    }

    private static boolean swapsThrones(Game game, Player seat, Player partner, Deal deal) {
        return deal.items().stream()
                .filter(item -> item.isFrom(partner.getFaction()) && item.isTo(seat.getFaction()))
                .map(item -> NotesForTrade.aliasOf(game, item))
                .flatMap(Optional::stream)
                .anyMatch(
                        alias -> alias.endsWith(NoteGiving.THRONE) && Standings.same(game.getPNOwner(alias), partner));
    }

    static int savedPoints(Game game, Player seat, Deal deal) {
        int net = deal.netTradeGoodsTo(seat.getFaction());
        if (net <= 0 || !ObjectivePolicy.scorablePublics(game, seat).isEmpty()) return 0;
        return Math.max(0, SpendUnlock.pointDelta(game, seat, net));
    }

    private static boolean pricedHighEnough(Game game, Player seat, Player partner, Deal deal) {
        if (deal.items().stream().anyMatch(item -> item.type().isDebt())) return false;
        double offered = deal.items().stream()
                .filter(item -> item.isTo(seat.getFaction()))
                .mapToDouble(item -> ComponentValues.receive(game, seat, partner, item, Trust.LENDING))
                .sum();
        return offered >= MIN_PRICE;
    }
}
