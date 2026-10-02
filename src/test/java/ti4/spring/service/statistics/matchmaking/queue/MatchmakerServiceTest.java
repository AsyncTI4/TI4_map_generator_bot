package ti4.spring.service.statistics.matchmaking.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.settings.users.UserSettings;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingEventService;
import ti4.testUtils.BaseTi4Test;

class MatchmakerServiceTest extends BaseTi4Test {

    private final List<QueuedParty> parties = new ArrayList<>();
    private long nextPartyId = 1;

    private MatchmakingQueueStore queueStore;
    private MatchmakerService matchmakerService;
    private MockedStatic<MatchmakingRatingEventService> ratingService;
    private MockedStatic<MatchmakingNotifier> notifier;

    @BeforeEach
    void setup() {
        queueStore = mock(MatchmakingQueueStore.class);
        when(queueStore.loadQueuedParties()).thenReturn(parties);
        matchmakerService = new MatchmakerService(queueStore);

        MatchmakingRatingEventService ratingServiceMock = mock(MatchmakingRatingEventService.class);
        when(ratingServiceMock.getPlayerRatings(any())).thenReturn(Map.of());
        ratingService = Mockito.mockStatic(MatchmakingRatingEventService.class);
        ratingService.when(MatchmakingRatingEventService::get).thenReturn(ratingServiceMock);

        notifier = Mockito.mockStatic(MatchmakingNotifier.class);
    }

    @AfterEach
    void closeStaticMocks() {
        notifier.close();
        ratingService.close();
    }

    @Test
    void processQueueDeletesExpiredPartiesThenNotifies() {
        long partyId = addSoloParty("p1", Duration.ofHours(9));

        matchmakerService.processQueue();

        verify(queueStore).deleteParties(List.of(partyId));
        ArgumentCaptor<List<QueuedParty>> expiredCaptor = ArgumentCaptor.captor();
        notifier.verify(() -> MatchmakingNotifier.notifyExpired(expiredCaptor.capture()));
        assertThat(expiredCaptor.getValue())
                .extracting(queuedParty -> queuedParty.party().getId())
                .containsExactly(partyId);
        ArgumentCaptor<List<MatchedGame>> gamesCaptor = ArgumentCaptor.captor();
        notifier.verify(() -> MatchmakingNotifier.postMatchedGames(gamesCaptor.capture()));
        assertThat(gamesCaptor.getValue()).isEmpty();
    }

    @Test
    void processQueueDeletesMatchedPartiesBeforePosting() {
        long firstPartyId = addSoloParty("p1", Duration.ZERO);
        long secondPartyId = addSoloParty("p2", Duration.ZERO);
        long thirdPartyId = addSoloParty("p3", Duration.ZERO);

        matchmakerService.processQueue();

        InOrder inOrder = Mockito.inOrder(queueStore, MatchmakingNotifier.class);
        ArgumentCaptor<List<Long>> deletedPartyIdsCaptor = ArgumentCaptor.captor();
        inOrder.verify(queueStore).deleteParties(deletedPartyIdsCaptor.capture());
        assertThat(deletedPartyIdsCaptor.getValue())
                .containsExactlyInAnyOrder(firstPartyId, secondPartyId, thirdPartyId);
        ArgumentCaptor<List<MatchedGame>> gamesCaptor = ArgumentCaptor.captor();
        inOrder.verify(notifier, () -> MatchmakingNotifier.postMatchedGames(gamesCaptor.capture()));
        assertThat(gamesCaptor.getValue()).hasSize(1);
        notifier.verify(() -> MatchmakingNotifier.notifyExpired(any()), never());
    }

    private long addSoloParty(String userId, Duration queueWait) {
        long partyId = nextPartyId;
        nextPartyId++;
        MatchmakingQueueMember member = new MatchmakingQueueMember();
        member.setUserId(userId);
        member.setPartyId(partyId);
        MatchmakingQueueParty party = new MatchmakingQueueParty();
        party.setId(partyId);
        party.setQueued(true);
        party.setQueuedAt(Instant.now().minus(queueWait));
        party.setLeaderId(userId);
        parties.add(new QueuedParty(party, List.of(member), settings()));
        return partyId;
    }

    private static UserSettings settings() {
        UserSettings settings = new UserSettings();
        settings.setMatchmakingPlayerCounts(List.of("3"));
        settings.setMatchmakingVictoryPointGoals(List.of("10"));
        settings.setMatchmakingExpansions(List.of(MatchmakingOptions.POK_AND_TE_EXPANSION_OPTION));
        settings.setMatchmakingPaces(List.of(MatchmakingOptions.SLOWER_PACE_OPTION));
        settings.setMatchmakingRestrictions(List.of());
        settings.setMatchmakingMaxQueueTime("8 hours");
        return settings;
    }
}
