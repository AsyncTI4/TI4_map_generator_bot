package ti4.ai;

import java.util.List;
import ti4.ai.brain.StrategyCard;
import ti4.ai.expedition.ExpeditionKind;
import ti4.game.Game;
import ti4.game.Player;

/**
 * The table on which pricing the hand used to recurse without end: Thunder's Edge with the action card slice still
 * open, the AI seat holding Strategize and a second card with a strategy token and influence to spare, Sol holding an
 * unplayed Leadership the seat could buy tokens from, and a third seat holding an unplayed Technology worth following.
 * Pricing the slice prices the hand, the hand holds Strategize, Strategize asks whether a token is spare, that asks
 * whether Technology is worth following, which asks how many trade goods are idle, which asks what Leadership would
 * buy, which asks what the slice would cost: the hand again.
 */
public final class HandPricingRecursionTable {

    public static final String THIRD_ID = "200000000000000002";
    public static final String STRATEGIZE = "strategize1";
    public static final String SECOND_CARD = "sabo1";
    private static final int STRATEGY_CARDS = 8;

    public final AiTestGame test;
    public final Game game;
    public final Player seat;
    public final Player leadershipHolder;
    public final Player technologyHolder;

    // Route one: Strategize would follow Trade, but first asks whether its token is better kept for Technology.
    public static HandPricingRecursionTable throughTechnology() {
        return new HandPricingRecursionTable(StrategyCard.TRADE, true);
    }

    // Route two: Strategize prices following Diplomacy, whose readying is weighed against Leadership tokens it buys.
    // Technology is out of play here, so only this route reaches the hand.
    public static HandPricingRecursionTable throughDiplomacy() {
        HandPricingRecursionTable table = new HandPricingRecursionTable(StrategyCard.DIPLOMACY, false);
        table.seat.exhaustPlanet("lodor");
        table.seat.exhaustPlanet("quann");
        return table;
    }

    private HandPricingRecursionTable(StrategyCard secondaryLeftOpen, boolean technologyInPlay) {
        test = new AiTestGame();
        game = test.game;
        game.setRound(2);
        test.aiIsActive("action");
        for (int card = 1; card <= STRATEGY_CARDS; card++) game.setScTradeGood(card, 0);
        seat = test.nekro;
        seat.setFaction(game, "sardakk");
        seat.addBreakthrough("sardakkbt");
        test.nekroHome();
        test.place("37", "201");
        seat.addPlanet("meer");
        seat.addPlanet("arinam");
        test.place("26", "202");
        seat.addPlanet("lodor");
        test.place("25", "203");
        seat.addPlanet("quann");
        seat.setTacticalCC(3);
        seat.setFleetCC(3);
        seat.setStrategicCC(2);
        seat.setTg(4);
        seat.setCommodities(0);
        seat.setActionCard(STRATEGIZE, 412);
        seat.setActionCard(SECOND_CARD, 413);

        leadershipHolder = test.sol;
        technologyHolder = test.addSeat(THIRD_ID, "xxcha", "red");
        leadershipHolder.addSC(initiativeOf(StrategyCard.LEADERSHIP));
        technologyHolder.addSC(initiativeOf(StrategyCard.TECHNOLOGY));
        onlyThese(
                StrategyCard.LEADERSHIP,
                technologyInPlay ? StrategyCard.TECHNOLOGY : StrategyCard.LEADERSHIP,
                secondaryLeftOpen);

        claimedByOthers(ExpeditionKind.TRADE_GOODS, "sol");
        claimedByOthers(ExpeditionKind.FIVE_RES, "xxcha");
        game.getExpeditions().getExpeditionFactions().put(ExpeditionKind.ACTION_CARDS.id(), null);
    }

    private void claimedByOthers(ExpeditionKind kind, String faction) {
        game.getExpeditions().getExpeditionFactions().put(kind.id(), faction);
    }

    public int initiativeOf(StrategyCard card) {
        for (int initiative : game.getSCList()) {
            if (StrategyCard.of(game, initiative) == card) return initiative;
        }
        throw new AssertionError(card + " is not in this game");
    }

    private void onlyThese(StrategyCard... cards) {
        List<StrategyCard> kept = List.of(cards);
        for (int initiative : game.getSCList()) {
            if (!kept.contains(StrategyCard.of(game, initiative))) game.setSCPlayed(initiative, true);
        }
    }
}
