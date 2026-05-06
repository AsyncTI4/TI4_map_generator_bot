package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatCandidateEntity;

class CombatReplayDiscordPostServiceTest {

    @Test
    void createReplayThreadSchedulesArchiveAfterTwoHours() {
        Message posted = mock(Message.class);
        ThreadChannelAction action = mock(ThreadChannelAction.class);
        ThreadChannel thread = mock(ThreadChannel.class);
        ThreadChannelManager manager = mock(ThreadChannelManager.class);
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(42L);
        candidate.setTilePosition("306");
        candidate.setAttackerFaction("mentak");
        candidate.setDefenderFaction("hacan");
        CombatReplayDiscordPostService service =
                new CombatReplayDiscordPostService(new CombatContestSettings(), mock(ReplayPayloadRenderer.class));

        when(posted.createThreadChannel("combat-archive-c42-t306-mentak-v-hacan"))
                .thenReturn(action);
        when(action.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS))
                .thenReturn(action);
        when(action.complete()).thenReturn(thread);
        when(thread.getManager()).thenReturn(manager);
        when(manager.setArchived(true)).thenReturn(manager);

        ThreadChannel result = service.createReplayThread(posted, candidate);

        assertSame(thread, result);
        verify(action).setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
        verify(manager).queueAfter(eq(2L), eq(TimeUnit.HOURS), any(Consumer.class), any(Consumer.class));
    }
}
