package ti4.contest.replay.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import org.junit.jupiter.api.Test;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.entities.CombatCandidateEntity;

class CombatReplayDiscordPostServiceTest {

    @Test
    void createReplayThreadUsesOneHourAutoArchiveDuration() {
        Message posted = mock(Message.class);
        ThreadChannelAction action = mock(ThreadChannelAction.class);
        ThreadChannel thread = mock(ThreadChannel.class);
        CombatCandidateEntity candidate = new CombatCandidateEntity();
        candidate.setId(42L);
        candidate.setTilePosition("306");
        candidate.setAttackerFaction("mentak");
        candidate.setDefenderFaction("hacan");
        CombatReplayDiscordPostService service =
                new CombatReplayDiscordPostService(new CombatContestSettings(), mock(ReplayPayloadRenderer.class));

        when(posted.createThreadChannel("combat-archive-c42-t306-mentak-v-hacan"))
                .thenReturn(action);
        when(action.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR))
                .thenReturn(action);
        when(action.complete()).thenReturn(thread);

        ThreadChannel result = service.createReplayThread(posted, candidate);

        assertSame(thread, result);
        verify(action).setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
    }
}
