package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameManager;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.PositionMapper;
import ti4.service.turn.StartTurnService;
import ti4.testUtils.BaseTi4Test;

// AI seats trade with each other end to end: six AI seats play in-process on the fake Discord, every offer is built,
// sent, answered and carried out by pressing the bot's real transaction buttons, and the test forces only the
// starting state of each scenario once the first action phase begins.
class TradeFlowSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("hacan", "sol", "jolnar", "xxcha", "letnev", "yin");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int MAX_TICKS = 3000;
    private static final int TRADE = 5;
    private static final int WASHED = 3;
    private static final int HACAN_TRADE_GOODS = 2;
    private static final String HACAN = "hacan";
    private static final String ACTION = "action";

    // The active seat and a neighbour each hold 3 commodities (2 for a faction that holds no more) and nothing else
    // anyone could trade. At the start of its turn the active seat offers a 1:1 wash, the neighbour accepts, and both
    // end with the other's commodities as trade goods.
    @Test
    void neighboursWashTheirCommodities() throws Exception {
        WashScenario wash = new WashScenario();
        SelfPlayGame.Outcome outcome = play(wash);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(wash.trade).as(outcome.report()).isPresent();
        SelfPlayEnvironment.TradeRecord trade = wash.trade.orElseThrow();
        assertThat(trade.from()).isEqualTo(wash.active);
        assertThat(trade.to()).isEqualTo(wash.neighbour);
        assertThat(trade.items())
                .containsExactlyInAnyOrder(
                        "sending" + wash.active + "_receiving" + wash.neighbour + "_Comms_" + wash.amount,
                        "sending" + wash.neighbour + "_receiving" + wash.active + "_Comms_" + wash.amount);
        assertThat(wash.handlers)
                .containsKeys("transaction", "transactWith", "newTransact", "sendOffer", "acceptOffer");
    }

    // A non-Hacan seat holds Trade, plays it at the start of the action phase (it has no tactic token to do anything
    // else) and announces its terms. Hacan holds 2 trade goods, so everyone has somewhere to wash commodities and the
    // free replenish is worth it: every AI follows for free and every bill is honoured. Nobody neighbours the holder
    // this early, so Hacan (Guild Ships) gets an even wash and the others sign their fee over as debt.
    @Test
    void everyFollowerSettlesTheTradeHoldersBill() throws Exception {
        TradeScenario trade = new TradeScenario();
        SelfPlayGame.Outcome outcome = play(trade);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(trade.followers).as(outcome.report()).isNotEmpty();
        assertThat(trade.settledWith).as(outcome.report()).containsAll(trade.followers);
        assertThat(trade.debtors).as(outcome.report()).isNotEmpty();
        assertThat(trade.holderItemsLeft).as(outcome.report()).isZero();
    }

    private static SelfPlayGame.Outcome play(Scenario scenario) throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(System.currentTimeMillis())) {
            SelfPlayGame selfPlay = new SelfPlayGame(
                    env, "aitrade" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
            scenario.attach(env, selfPlay);
            try {
                selfPlay.setUp(FACTIONS);
                SelfPlayGame.Outcome outcome = selfPlay.run(scenario, VIRTUAL_DAY, MAX_TICKS);
                System.out.println(outcome.report());
                return outcome;
            } finally {
                AiRuntime.forget(selfPlay.name);
                GameManager.delete(selfPlay.name);
            }
        }
    }

    /** Forces its starting state once the first action phase begins, then stops when its trade is done. */
    private abstract static class Scenario implements Predicate<Game> {

        SelfPlayEnvironment env;
        SelfPlayGame selfPlay;
        private boolean forced;

        void attach(SelfPlayEnvironment env, SelfPlayGame selfPlay) {
            this.env = env;
            this.selfPlay = selfPlay;
        }

        @Override
        public boolean test(Game game) {
            if (!forced) {
                if (!ACTION.equalsIgnoreCase(game.getPhaseOfGame()) || game.getActivePlayer() == null) return false;
                forced = true;
                selfPlay.force(game, this::force);
                return false;
            }
            return done(game);
        }

        abstract void force(Game game);

        abstract boolean done(Game game);

        List<SelfPlayEnvironment.TradeRecord> ledger() {
            synchronized (env.tradeLedger) {
                return List.copyOf(env.tradeLedger);
            }
        }

        static Player player(Game game, String faction) {
            return game.getPlayerFromColorOrFaction(faction);
        }

        /** A ship of {@code visitor} in a system next to {@code host}'s home makes them neighbours. */
        static void beside(Game game, Player host, Player visitor) {
            Tile home = host.getHomeSystemTile();
            Tile next = PositionMapper.getAdjacentTilePositions(home.getPosition()).stream()
                    .map(game::getTileByPosition)
                    .filter(tile -> tile != null && tile != home)
                    .findFirst()
                    .orElseThrow();
            next.addUnit("space", Units.getUnitKey(UnitType.Destroyer, visitor.getColor()), 1);
        }
    }

    private static final class WashScenario extends Scenario {

        String active;
        String neighbour;
        int amount;
        Optional<SelfPlayEnvironment.TradeRecord> trade = Optional.empty();
        Map<String, Integer> handlers = Map.of();

        @Override
        void force(Game game) {
            Player first = game.getActivePlayer();
            Player next = game.getRealPlayers().stream()
                    .filter(player -> player != first && !HACAN.equals(player.getFaction()))
                    .filter(player -> player.getCommoditiesTotal() >= WASHED)
                    .findFirst()
                    .orElseThrow();
            active = first.getFaction();
            neighbour = next.getFaction();
            beside(game, first, next);
            for (Player player : game.getRealPlayers()) {
                player.setCommodities(0);
                player.setTg(0);
            }
            amount = Math.min(WASHED, Math.min(first.getCommoditiesTotal(), next.getCommoditiesTotal()));
            first.setCommodities(amount);
            next.setCommodities(amount);
        }

        @Override
        boolean done(Game game) {
            trade = ledger().stream()
                    .filter(record ->
                            record.from().equals(active) && record.to().equals(neighbour))
                    .findFirst();
            Player first = player(game, active);
            Player next = player(game, neighbour);
            boolean finished = trade.isPresent()
                    && first.getTg() >= amount
                    && next.getTg() >= amount
                    && first.getTransactionItems().isEmpty()
                    && next.getTransactionItems().isEmpty();
            if (finished) handlers = Map.copyOf(env.pressedHandlers);
            return finished;
        }
    }

    private static final class TradeScenario extends Scenario {

        String holder;
        Set<String> followers = Set.of();
        Set<String> settledWith = Set.of();
        Set<String> debtors = Set.of();
        int holderItemsLeft = -1;

        @Override
        void force(Game game) {
            Player chosen = game.getRealPlayers().stream()
                    .filter(player -> !HACAN.equals(player.getFaction()))
                    .findFirst()
                    .orElseThrow();
            holder = chosen.getFaction();
            int previous = chosen.getSCs().iterator().next();
            Player tradeOwner = ti4.helpers.Helper.getPlayerWithThisSC(game, TRADE);
            if (tradeOwner != null && tradeOwner != chosen) {
                tradeOwner.removeSC(TRADE);
                tradeOwner.addSC(previous);
            }
            chosen.clearSCs();
            chosen.addSC(TRADE);
            chosen.setTacticalCC(0);
            player(game, HACAN).setTg(HACAN_TRADE_GOODS);
            StartTurnService.turnStart(selfPlay.ownerEvent(), game, chosen);
        }

        @Override
        boolean done(Game game) {
            Player tradeHolder = player(game, holder);
            if (!game.getPlayedSCs().contains(TRADE)) return false;
            boolean everyoneAnswered = game.getRealPlayers().stream()
                    .filter(player -> player != tradeHolder)
                    .allMatch(player -> player.hasFollowedSC(TRADE));
            if (!everyoneAnswered) return false;
            String followedKey = "followedSC" + TRADE + "_" + game.getRound();
            followers = Arrays.stream(game.getStoredValue(followedKey).split("_"))
                    .filter(faction -> !faction.isEmpty() && !faction.equals(holder))
                    .collect(Collectors.toSet());
            settledWith = ledger().stream()
                    .filter(record -> record.from().equals(holder))
                    .map(SelfPlayEnvironment.TradeRecord::to)
                    .collect(Collectors.toSet());
            debtors = followers.stream()
                    .filter(faction ->
                            tradeHolder.getDebtTokenCount(player(game, faction).getColor()) > 0)
                    .collect(Collectors.toSet());
            holderItemsLeft = tradeHolder.getTransactionItems().size();
            return !followers.isEmpty() && settledWith.containsAll(followers) && holderItemsLeft == 0;
        }
    }
}
