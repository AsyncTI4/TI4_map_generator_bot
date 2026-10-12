package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.helpers.ActionCardHelper;
import ti4.testUtils.BaseTi4Test;

// Six AI seats play in-process on the fake Discord. Once the first action phase begins the test plays one card for the
// active seat, the way a human player would through the bot, and the AI seats answer its Sabotage window with the
// bot's real buttons.
class SabotageSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("hacan", "sol", "jolnar", "xxcha", "letnev", "yin");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int MAX_TICKS = 3000;
    private static final int CARD_INDEX = 9701;
    private static final int SABOTAGE_INDEX = 9702;
    private static final String OVERRULE = "Overrule";
    private static final String ACTION = "action";

    // A player two points short of winning plays Overrule. Every seat that holds a Sabotage sees what it could cost
    // the table, and one of them cancels the card.
    @Test
    void aSeatCancelsAContendersOverrule() throws Exception {
        ContenderPlaysOverrule scenario = new ContenderPlaysOverrule();
        SelfPlayGame.Outcome outcome = play(scenario);

        assertThat(outcome.ending()).as(outcome.report()).isEqualTo(SelfPlayGame.Ending.STOP_CONDITION);
        assertThat(scenario.canceled).as(outcome.report()).isTrue();
        assertThat(scenario.handlers).as(outcome.report()).containsKey("sabotage");
        assertThat(scenario.handlers).as(outcome.report()).doesNotContainKey("resolveOverrule");
    }

    private static SelfPlayGame.Outcome play(ContenderPlaysOverrule scenario) throws Exception {
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(System.currentTimeMillis())) {
            SelfPlayGame selfPlay =
                    new SelfPlayGame(env, "aisabo" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
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

    private static final class ContenderPlaysOverrule implements Predicate<Game> {

        private SelfPlayEnvironment env;
        private SelfPlayGame selfPlay;
        private boolean forced;
        boolean canceled;
        Map<String, Integer> handlers = Map.of();

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
            canceled = game.getGameStats().getActionCardPlays().stream()
                    .anyMatch(play -> OVERRULE.equals(play.getActionCard()) && play.isCanceled());
            if (canceled) handlers = Map.copyOf(env.pressedHandlers);
            return canceled;
        }

        private void force(Game game) {
            Player contender = game.getActivePlayer();
            game.scorePublicObjective(contender.getUserID(), game.addCustomPO("Test points", game.getVp() - 2));
            contender.setActionCard("overrule", CARD_INDEX);
            game.getRealPlayers().stream()
                    .filter(player -> player != contender)
                    .findFirst()
                    .orElseThrow()
                    .setActionCard("sabo1", SABOTAGE_INDEX);
            ActionCardHelper.playAC(
                    selfPlay.ownerEvent(), game, contender, String.valueOf(CARD_INDEX), game.getMainGameChannel());
        }
    }
}
