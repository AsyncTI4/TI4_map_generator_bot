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
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        matchmakerService = new MatchmakerService(queueStore, new PendingMatchPosts());

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
        assertThat(capturePostedMatches().games()).isEmpty();
    }

    // Matched parties used to be deleted before the post was attempted, so a missing forum, a Discord error or the
    // bot stopping mid-post silently dropped every player in the match. They now leave the queue only once the
    // forum post exists.
    @Test
    void matchedPartiesStayQueuedUntilThePostSucceeds() {
        long firstPartyId = addSoloParty("p1", Duration.ZERO);
        long secondPartyId = addSoloParty("p2", Duration.ZERO);
        long thirdPartyId = addSoloParty("p3", Duration.ZERO);

        matchmakerService.processQueue();

        verify(queueStore, never()).deleteParties(any());
        PostedMatches posted = capturePostedMatches();
        assertThat(posted.games()).hasSize(1);
        notifier.verify(() -> MatchmakingNotifier.notifyExpired(any()), never());

        posted.outcome().posted(posted.games().getFirst());

        ArgumentCaptor<List<Long>> deletedPartyIdsCaptor = ArgumentCaptor.captor();
        verify(queueStore).deleteParties(deletedPartyIdsCaptor.capture());
        assertThat(deletedPartyIdsCaptor.getValue())
                .containsExactlyInAnyOrder(firstPartyId, secondPartyId, thirdPartyId);
    }

    // While the post is in flight the parties are still in the database, so they must not be matched a second time
    // by the next queue run (or picked up by a standing player search).
    @Test
    void partiesWithAPostInFlightAreNotMatchedAgain() {
        addSoloParty("p1", Duration.ZERO);
        addSoloParty("p2", Duration.ZERO);
        addSoloParty("p3", Duration.ZERO);

        matchmakerService.processQueue();
        matchmakerService.processQueue();

        List<PostedMatches> runs = capturePostedMatchesPerRun(2);
        assertThat(runs.get(0).games()).hasSize(1);
        assertThat(runs.get(1).games()).isEmpty();
    }

    @Test
    void aFailedPostKeepsThePartiesQueuedForTheNextRun() {
        addSoloParty("p1", Duration.ZERO);
        addSoloParty("p2", Duration.ZERO);
        addSoloParty("p3", Duration.ZERO);

        matchmakerService.processQueue();
        PostedMatches firstRun = capturePostedMatches();
        firstRun.outcome().notPosted(firstRun.games().getFirst());
        matchmakerService.processQueue();

        verify(queueStore, never()).deleteParties(any());
        List<PostedMatches> runs = capturePostedMatchesPerRun(2);
        assertThat(runs.get(1).games()).hasSize(1);
    }

    @Test
    void aMemberWhoLeftTheServerOnlyRemovesTheirOwnParty() {
        long departedPartyId = addSoloParty("p1", Duration.ZERO);
        addSoloParty("p2", Duration.ZERO);
        addSoloParty("p3", Duration.ZERO);

        matchmakerService.processQueue();
        PostedMatches firstRun = capturePostedMatches();
        firstRun.outcome().membersLeftGuild(firstRun.games().getFirst(), List.of(departedPartyId));

        verify(queueStore).deleteParties(List.of(departedPartyId));
        // The deleted party is gone from the real store; drop it from the fake one so the next run sees the rest.
        parties.removeIf(party -> party.party().getId() == departedPartyId);
        addSoloParty("p4", Duration.ZERO);
        matchmakerService.processQueue();

        List<PostedMatches> runs = capturePostedMatchesPerRun(2);
        assertThat(runs.get(1).games())
                .singleElement()
                .satisfies(game -> assertThat(game.members())
                        .extracting(MatchmakingQueueMember::getUserId)
                        .containsExactlyInAnyOrder("p2", "p3", "p4"));
    }

    // Groups used to be refused with "You cannot queue for TIGL as a group".
    @Test
    void aFormedGroupCanQueueForTigl() {
        MatchmakingQueueMember member = new MatchmakingQueueMember();
        member.setUserId("p1");
        member.setPartyId(7L);
        MatchmakingQueueParty group = new MatchmakingQueueParty();
        group.setId(7L);
        when(queueStore.findMember("p1")).thenReturn(Optional.of(member));
        when(queueStore.findParty(7L)).thenReturn(Optional.of(group));

        Optional<String> error = matchmakerService.queue("p1", true);

        assertThat(error).isEmpty();
        verify(queueStore).markQueued(group, "p1", true);
    }

    private record PostedMatches(List<MatchedGame> games, MatchmakingNotifier.MatchPostOutcome outcome) {}

    private PostedMatches capturePostedMatches() {
        return capturePostedMatchesPerRun(1).getFirst();
    }

    private List<PostedMatches> capturePostedMatchesPerRun(int runs) {
        ArgumentCaptor<List<MatchedGame>> gamesCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<MatchmakingNotifier.MatchPostOutcome> outcomeCaptor = ArgumentCaptor.captor();
        notifier.verify(
                () -> MatchmakingNotifier.postMatchedGames(gamesCaptor.capture(), outcomeCaptor.capture()),
                Mockito.times(runs));
        List<PostedMatches> posted = new ArrayList<>();
        for (int run = 0; run < runs; run++) {
            posted.add(new PostedMatches(
                    gamesCaptor.getAllValues().get(run),
                    outcomeCaptor.getAllValues().get(run)));
        }
        return posted;
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
