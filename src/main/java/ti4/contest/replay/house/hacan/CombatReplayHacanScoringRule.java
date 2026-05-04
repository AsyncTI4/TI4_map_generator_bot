package ti4.contest.replay.house.hacan;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.core.CombatReplayHouse;
import ti4.contest.replay.entities.CombatContestSideBetEntity;
import ti4.contest.replay.service.CombatReplayHouseScoringContext;
import ti4.contest.replay.service.CombatReplayHouseScoringRule;
import ti4.contest.replay.service.CombatReplaySideBetService.ResolvedSideBet;

@Service
@Order(30)
@RequiredArgsConstructor
public class CombatReplayHacanScoringRule implements CombatReplayHouseScoringRule {

    private final CombatContestSettings settings;
    private final CombatReplayHacanMarketCompactService marketCompactService;
    private final CombatReplayHacanTradeConvoysService tradeConvoysService;

    @Override
    public void apply(CombatReplayHouseScoringContext context) {
        applySideBetAbilities(context);
        applyTradeConvoysFavorTransfer(context);
        applyTradeConvoysPoints(context);
    }

    private void applySideBetAbilities(CombatReplayHouseScoringContext context) {
        int hacanInsiderTradingPoints = 0;
        for (CombatContestSideBetEntity sideBet : context.sideBets()) {
            if (context.houseForUser(sideBet.getDiscordUserId()) == CombatReplayHouse.HACAN) {
                hacanInsiderTradingPoints += settings.getSideBets().getCostPoints();
            }
        }

        int favorOnHit = marketCompactService.favorOnHit();
        Set<CombatReplayHacanMarketCompactService.MarkedSideBet> markedSideBetHits = new HashSet<>();
        for (ResolvedSideBet sideBet : context.resolvedSideBets()) {
            if (favorOnHit > 0
                    && marketCompactService.isMarked(
                            context.contest().getId(), sideBet.betType(), sideBet.targetFaction())) {
                CombatReplayHacanMarketCompactService.MarkedSideBet hit =
                        new CombatReplayHacanMarketCompactService.MarkedSideBet(
                                sideBet.betType(), context.normalizeFaction(sideBet.targetFaction()));
                if (markedSideBetHits.add(hit)) {
                    context.totals(CombatReplayHouse.HACAN).favorPoints += favorOnHit;
                    context.addFavorSummary(CombatReplayHouse.HACAN, "Market Compact hits", favorOnHit, false);
                }
            }
        }

        context.addAbilitySummary(CombatReplayHouse.HACAN, "Insider Trading", hacanInsiderTradingPoints);
        context.addAbilitySummary(
                CombatReplayHouse.HACAN,
                "Market Compact",
                marketCompactService.marketMakerPoints(context.contest().getId(), context.sideBets()));
    }

    private void applyTradeConvoysFavorTransfer(CombatReplayHouseScoringContext context) {
        CombatReplayHacanTradeConvoysService.TradeConvoys tradeConvoys =
                tradeConvoysService.tradeConvoysForContest(context.contest().getId());
        if (!tradeConvoys.active()) return;
        context.totals(tradeConvoys.targetHouse()).favorPoints += tradeConvoys.favorCost();
        context.addFavorSummary(tradeConvoys.targetHouse(), "Trade Convoys", tradeConvoys.favorCost(), false);
    }

    private void applyTradeConvoysPoints(CombatReplayHouseScoringContext context) {
        CombatReplayHacanTradeConvoysService.TradeConvoys tradeConvoys =
                tradeConvoysService.tradeConvoysForNextCombat(context.contest().getId());
        if (!tradeConvoys.active()) return;

        int bonusPoints = CombatReplayHacanTradeConvoysService.tradeConvoysBonusPoints(
                context.earnedPointsForHouse(tradeConvoys.targetHouse()), tradeConvoys.bonusPercent());
        context.addAbilitySummary(CombatReplayHouse.HACAN, "Hacan Trade Convoys", bonusPoints);
    }
}
