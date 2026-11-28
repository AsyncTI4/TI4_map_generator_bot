package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import ti4.ai.brain.AiMemory;
import ti4.ai.brain.AiTurnContext;
import ti4.testUtils.BaseTi4Test;

class TrustTest extends BaseTi4Test {

    private static final double CLOSE = 1e-9;
    private static final int MEMORY_CAPACITY = 500;

    // AI seats always keep their deals, so they are fully trusted and nothing about them is stored.
    @Test
    void aiSeatsAreAlwaysTrusted() {
        TradeTable table = TradeTable.withAiSol();
        AiTurnContext context = table.context();

        Trust.record(context, table.sol, Trust.Event.COLLECTION_UNPAID);

        assertThat(Trust.of(context, table.sol)).isCloseTo(1.0, within(CLOSE));
        assertThat(context.memory().has(Trust.KEY)).isFalse();
    }

    @Test
    void humansStartAtPointEight() {
        TradeTable table = TradeTable.withHumanSol();

        assertThat(Trust.of(table.context(), table.sol)).isCloseTo(0.8, within(CLOSE));
    }

    // Accepting an offer or paying down debt earns trust; letting a settlement or a debt collection expire while
    // able to pay loses it.
    @Test
    void eachEventMovesTrust() {
        TradeTable table = TradeTable.withHumanSol();
        AiTurnContext context = table.context();

        Trust.record(context, table.sol, Trust.Event.ACCEPTED_OFFER);
        assertThat(Trust.of(context, table.sol)).isCloseTo(0.9, within(CLOSE));
        Trust.record(context, table.sol, Trust.Event.FREE_FOLLOW_UNPAID);
        assertThat(Trust.of(context, table.sol)).isCloseTo(0.6, within(CLOSE));
        Trust.record(context, table.sol, Trust.Event.PAID_DEBT);
        assertThat(Trust.of(context, table.sol)).isCloseTo(0.7, within(CLOSE));
        Trust.record(context, table.sol, Trust.Event.COLLECTION_UNPAID);
        assertThat(Trust.of(context, table.sol)).isCloseTo(0.5, within(CLOSE));
        Trust.record(context, table.sol, Trust.Event.EXCLUDED_REPLENISH_UNPAID);
        assertThat(Trust.of(context, table.sol)).isCloseTo(0.2, within(CLOSE));
        assertThat(context.memory().get(Trust.KEY)).contains("sol=0.20");
    }

    @Test
    void staysBetweenZeroAndOne() {
        TradeTable table = TradeTable.withHumanSol();
        AiTurnContext context = table.context();

        for (int i = 0; i < 5; i++) Trust.record(context, table.sol, Trust.Event.ACCEPTED_OFFER);
        assertThat(Trust.of(context, table.sol)).isCloseTo(1.0, within(CLOSE));

        for (int i = 0; i < 5; i++) Trust.record(context, table.sol, Trust.Event.FREE_FOLLOW_UNPAID);
        assertThat(Trust.of(context, table.sol)).isCloseTo(0.0, within(CLOSE));
    }

    // AI memory forgets its oldest keys first (500 of them), and writing a key again does not make it newer. Trust is
    // rewritten whenever it changes and once a round, so a long game does not quietly forgive a player.
    @Test
    void trustSurvivesALongGameWithTheRoundlyRefresh() {
        TradeTable table = TradeTable.withHumanSol();
        AiTurnContext context = table.context();
        Trust.record(context, table.sol, Trust.Event.FREE_FOLLOW_UNPAID);
        Trust.refresh(context);

        fill(context.memory(), "round3-", MEMORY_CAPACITY - 50);
        table.game.setRound(4);
        Trust.refresh(context);
        fill(context.memory(), "round4-", 600 - (MEMORY_CAPACITY - 50));

        assertThat(Trust.of(context, table.sol)).isCloseTo(0.5, within(CLOSE));
    }

    // Without a new round to refresh it, 600 newer keys push the trust record out and the player is back at 0.8.
    @Test
    void withoutTheRefreshTrustIsForgotten() {
        TradeTable table = TradeTable.withHumanSol();
        AiTurnContext context = table.context();
        Trust.record(context, table.sol, Trust.Event.FREE_FOLLOW_UNPAID);
        Trust.refresh(context);

        fill(context.memory(), "same-round-", 600);
        Trust.refresh(context);

        assertThat(Trust.of(context, table.sol)).isCloseTo(0.8, within(CLOSE));
    }

    private static void fill(AiMemory memory, String prefix, int count) {
        for (int i = 0; i < count; i++) memory.put(prefix + i, "x");
    }
}
