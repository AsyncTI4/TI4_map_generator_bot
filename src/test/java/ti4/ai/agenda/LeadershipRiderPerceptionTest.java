package ti4.ai.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiSettings;
import ti4.ai.AiTestGame;
import ti4.ai.brain.StandardBrain;
import ti4.ai.perception.AiPerception;
import ti4.ai.perception.AiPrompt;
import ti4.discord.JdaService;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.testUtils.BaseTi4Test;
import ti4.testUtils.discord.FakeDiscord;

// A winning Leadership Rider posts two messages in the main channel: a line without buttons announcing the payout,
// then the gain buttons. RiderPayouts only presses the gain buttons after it has seen the announcement, so the
// announcement must survive perception. RiderPayoutsTest hands its prompts straight to the rule; this test reads them
// back through AiPerception the way a live seat does. Before the fix the buttonless line was dropped there and the
// AI never gained its 3 command tokens.
class LeadershipRiderPerceptionTest extends BaseTi4Test {

    private final AtomicLong clock = new AtomicLong(1_800_000_000_000L);
    private AiTestGame test;
    private TextChannel actions;
    private JDA savedJda;
    private Guild savedGuild;

    @BeforeEach
    void setUp() {
        savedJda = JdaService.jda;
        savedGuild = JdaService.guildPrimary;
        FakeDiscord discord = new FakeDiscord(clock::get);
        JdaService.jda = discord.jda();
        JdaService.guildPrimary = discord.guild();
        actions = discord.createTextChannel("ai-test-game-actions");
        test = new AiTestGame();
        test.game.setMainChannelID(actions.getId());
        Player nekro = test.nekro;
        nekro.setTacticalCC(3);
        nekro.setFleetCC(3);
        nekro.setStrategicCC(2);
        test.game.setStoredValue("originalCCsFornekro", nekro.getCCRepresentation());
    }

    @AfterEach
    void restoreDiscord() {
        JdaService.jda = savedJda;
        JdaService.guildPrimary = savedGuild;
    }

    @Test
    void gainsTheLeadershipRiderTokensFromTheRealMessages() {
        Player nekro = test.nekro;
        // The same two messages AgendaHelper sends when the rider wins.
        actions.sendMessage(nekro.getRepresentationUnfogged()
                        + " resolve _Leadership Rider_ by using the button to gain 3 command tokens.")
                .complete();
        clock.addAndGet(1000);
        actions.sendMessage(nekro.getRepresentationUnfogged() + ", your current command tokens are "
                        + nekro.getCCRepresentation() + ". Use buttons to gain command tokens.")
                .setComponents(ActionRow.of(ButtonHelper.getGainCCButtons(nekro)))
                .complete();
        clock.addAndGet(1000);

        List<AiPrompt> prompts =
                AiPerception.collect(test.game, nekro, new StandardBrain().publicWindowHandlerPrefixes());

        assertThat(prompts).anyMatch(AiPerception::isLeadershipRiderPayout);
        AiPrompt[] seen = prompts.toArray(AiPrompt[]::new);
        assertThat(RiderPayouts.leadership(test.contextFor(nekro, Set.of(), clock.get(), seen))
                        .map(AiTestGame::pressedId))
                .hasValueSatisfying(id -> assertThat(id).startsWith("FFCC_nekro_increase_"));
    }

    // The AI reads the last AiSettings.HISTORY_SIZE messages of the main channel. If enough other messages follow the
    // payout before the seat looks, the buttonless announcement drops out of that window while the gain buttons are
    // still in it. The seat's Leadership Rider, still listed in the agenda's votes until the next agenda is revealed,
    // tells it the gain prompt is the payout.
    @Test
    void gainsTheTokensWhenTheAnnouncementHasScrolledOutOfTheHistoryRead() {
        Player nekro = test.nekro;
        test.game.setCurrentAgendaVote("for", "nekro_Leadership Rider");
        actions.sendMessage(nekro.getRepresentationUnfogged()
                        + " resolve _Leadership Rider_ by using the button to gain 3 command tokens.")
                .complete();
        clock.addAndGet(1000);
        actions.sendMessage(nekro.getRepresentationUnfogged() + ", your current command tokens are "
                        + nekro.getCCRepresentation() + ". Use buttons to gain command tokens.")
                .setComponents(ActionRow.of(ButtonHelper.getGainCCButtons(nekro)))
                .complete();
        for (int message = 0; message < AiSettings.HISTORY_SIZE - 1; message++) {
            clock.addAndGet(10);
            actions.sendMessage("Agenda resolution line " + message).complete();
        }

        List<AiPrompt> prompts =
                AiPerception.collect(test.game, nekro, new StandardBrain().publicWindowHandlerPrefixes());

        assertThat(prompts).noneMatch(AiPerception::isLeadershipRiderPayout);
        assertThat(RiderPayouts.leadership(
                                test.contextFor(nekro, Set.of(), clock.get(), prompts.toArray(AiPrompt[]::new)))
                        .map(AiTestGame::pressedId))
                .hasValueSatisfying(id -> assertThat(id).startsWith("FFCC_nekro_increase_"));
    }
}
