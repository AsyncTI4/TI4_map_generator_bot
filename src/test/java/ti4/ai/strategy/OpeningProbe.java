package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ti4.ai.brain.StrategyCard;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.Mapper;

// Test-only diagnostics for the opening: the pick values a seat sees and why Gravity Drive is or is not credited.
public final class OpeningProbe {

    private OpeningProbe() {}

    public static String pickValues(Game game, Player seat) {
        List<Integer> held = new ArrayList<>();
        game.getRealPlayers().forEach(player -> held.addAll(player.getSCs()));
        List<String> parts = new ArrayList<>();
        PlanInputs in = PlanInputs.of(game, seat, PlanMoment.PICK);
        for (int initiative : game.getSCList()) {
            if (held.contains(initiative)) continue;
            CardPlan plan = StrategyCardPlanner.plan(in, initiative);
            double value = StrategyCardRanking.value(game, seat, initiative);
            parts.add(String.format(
                    Locale.ROOT,
                    "%d=%.2f [prior %+.2f, tg %d] %s",
                    initiative,
                    value,
                    PickPrior.nudge(plan.card(), game.getRound()),
                    game.getScTradeGoods().getOrDefault(initiative, 0),
                    plan.describe()));
        }
        return String.join(" | ", parts);
    }

    public static String goals(Game game, Player seat) {
        if (seat.getHomeSystemTile() == null) return "no home";
        java.util.Map<String, Integer> reach = ti4.ai.eval.MovementGraph.reach(
                game, seat, seat.getHomeSystemTile().getPosition(), 3);
        List<String> parts = new ArrayList<>();
        for (ti4.ai.tactical.RoundGoals.Goal goal :
                ti4.ai.tactical.RoundGoals.of(game, seat).goals()) {
            parts.add(String.format(
                    Locale.ROOT,
                    "%s d%s v%.1f p%d%s",
                    goal.position(),
                    reach.getOrDefault(goal.position(), -1),
                    goal.value(),
                    goal.planets(),
                    goal.needsTransport() ? "" : " presence"));
        }
        return String.join(", ", parts);
    }

    public static String rankedPlans(Game game, Player seat, int limit) {
        return ti4.ai.tactical.TacticalPlanner.ranked(game, seat).stream()
                .limit(limit)
                .map(plan -> String.format(
                        Locale.ROOT,
                        "%s %s %.2f moves %s land %s",
                        plan.kind(),
                        plan.target(),
                        plan.score(),
                        plan.moves().stream()
                                .map(move -> move.origin() + ":" + move.count() + move.type() + "@" + move.holder())
                                .toList(),
                        plan.landings()))
                .collect(java.util.stream.Collectors.joining(" || "));
    }

    public static String homeState(Game game, Player seat) {
        ti4.game.Tile home = seat.getHomeSystemTile();
        if (home == null) return "no home";
        int ground = home.getUnitHolders().values().stream()
                .mapToInt(holder -> ti4.ai.eval.BoardView.groundForces(holder, seat))
                .sum();
        long goals = ti4.ai.tactical.RoundGoals.of(game, seat).goals().stream()
                .filter(ti4.ai.tactical.RoundGoals.Goal::needsTransport)
                .count();
        return "home carriers "
                + ti4.ai.eval.BoardView.count(
                        ti4.ai.eval.BoardView.space(home), seat, ti4.helpers.Units.UnitType.Carrier)
                + " ground " + ground + " garrison "
                + ti4.ai.tactical.TacticalPlanner.groundKeptHomeWhileTransportsLeave(game, seat)
                + " transport goals " + goals + " awaitsGD " + OpeningHold.awaitsGravityDrive(game, seat)
                + " | " + goals(game, seat);
    }

    public static String gravityDrive(Game game, Player seat) {
        String best = ResearchPolicy.bestResearchable(game, seat).orElse("none");
        boolean prereq = Mapper.getTech("gd") != null
                && ResearchPolicy.missingPrerequisites(seat, Mapper.getTech("gd"), null) == 0;
        boolean affordFollow = seat.getStrategicCC() > 0
                && ScoringReserve.planAfterReserve(game, seat, SpendCost.resources(StrategyCardRules.FOLLOW_TECH_COST))
                        .isPresent();
        Wallet wallet = Wallet.of(game, seat);
        return String.format(
                Locale.ROOT,
                "gd prereq %s, raisable %d, holderGain %.2f, holdsTech %s, affordFollow %s (wallet res %d tg %d, reserve %s), gain GD %.2f EXTRA %.2f"
                        + " HOME %.2f, planetValue none %.2f gd %.2f, bestResearchable %s (%.2f; gd %.2f),"
                        + " researchesGdForOpening %s, wantsTechSoon %s, wantsToFollowTech %s, tokens t%d s%d f%d",
                prereq,
                OpeningOutlook.raisableTradeGoods(game, seat),
                OpeningOutlook.gravityDriveGainAsTechnologyHolder(game, seat),
                seat.getSCs().stream().anyMatch(i -> StrategyCard.of(game, i) == StrategyCard.TECHNOLOGY),
                affordFollow,
                wallet.resources(),
                seat.getTg(),
                ScoringReserve.reserved(game, seat).map(Object::toString).orElse("none"),
                OpeningOutlook.gain(game, seat, OpeningOutlook.Boost.GRAVITY_DRIVE),
                OpeningOutlook.gain(game, seat, OpeningOutlook.Boost.EXTRA_ACTION),
                OpeningOutlook.gain(game, seat, OpeningOutlook.Boost.HOME_PRODUCTION),
                OpeningOutlook.planetValue(game, seat, OpeningOutlook.Boost.NONE),
                OpeningOutlook.planetValue(game, seat, OpeningOutlook.Boost.GRAVITY_DRIVE),
                best,
                "none".equals(best) ? 0 : ResearchPolicy.researchValue(game, seat, best),
                ResearchPolicy.researchValue(game, seat, "gd"),
                CardTiming.researchesGravityDriveForTheOpening(game, seat),
                OpeningNeeds.wantsTechnologySoon(game, seat),
                StrategyCardRules.wantsToFollow(game, seat, StrategyCard.TECHNOLOGY),
                seat.getTacticalCC(),
                seat.getStrategicCC(),
                seat.getFleetCC());
    }
}
