package ti4.ai.perception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.discord.JdaService;
import ti4.helpers.GameClock;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.discord.FakeDiscord;

// The AI reads a strategy card's thread only while its latest message is under 15 minutes old. Self-play runs on a
// virtual clock that is months behind or hours ahead of the wall clock, so that age must be measured on the game clock:
// on the wall clock the threads read depended on how long the batch had been running, and a fixed clock start in the
// past hid every strategy thread from the AI.
class StrategyThreadClockTest extends BaseTi4Test {

    private static final long FIFTEEN_MINUTES = 15 * 60_000L;
    private final AtomicLong clock = new AtomicLong(1_790_000_000_000L);
    private AiTestGame test;
    private TextChannel actions;
    private JDA savedJda;
    private Guild savedGuild;

    @BeforeEach
    void setUp() {
        savedJda = JdaService.jda;
        savedGuild = JdaService.guildPrimary;
        GameClock.useForThisThread(clock::get);
        FakeDiscord discord = new FakeDiscord(clock::get);
        JdaService.jda = discord.jda();
        JdaService.guildPrimary = discord.guild();
        actions = discord.createTextChannel("ai-test-game-actions");
        test = new AiTestGame();
        test.game.setMainChannelID(actions.getId());
        test.game.setSCPlayed(1, true);
    }

    @AfterEach
    void restore() {
        GameClock.useWallClockForThisThread();
        JdaService.jda = savedJda;
        JdaService.guildPrimary = savedGuild;
    }

    private ThreadChannel leadershipThread() {
        String name = test.game.getName() + "-round-" + test.game.getRound() + "-leadership";
        ThreadChannel thread = actions.createThreadChannel(
                        name,
                        actions.sendMessage("Leadership played").complete().getIdLong())
                .complete();
        thread.sendMessage("Follow Leadership?").complete();
        return thread;
    }

    @Test
    void aThreadActiveMinutesAgoOnTheGameClockIsRead() {
        ThreadChannel thread = leadershipThread();
        clock.addAndGet(5 * 60_000L);

        assertThat(AiPerception.recentStrategyThreads(test.game)).containsExactly(thread);
    }

    @Test
    void aThreadQuietForFifteenMinutesOnTheGameClockIsNot() {
        leadershipThread();
        clock.addAndGet(FIFTEEN_MINUTES + 60_000L);

        assertThat(AiPerception.recentStrategyThreads(test.game)).isEmpty();
    }
}
