package ti4.spring.service.tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.spring.service.tournament.TournamentService.JoinOutcome;
import ti4.spring.service.tournament.TournamentService.LeaveResult;

class TournamentServiceTest {

    private static final long ID = 1;

    private final List<TournamentEntry> stored = new ArrayList<>();
    private final Tournament tournament = new Tournament();
    private TournamentService service;
    private long nextEntryId = 1;
    private Instant clock = Instant.parse("2026-11-13T00:00:00Z");

    @BeforeEach
    void setup() {
        tournament.setId(ID);
        tournament.setTargetSize(2);
        TournamentRepository tournaments = mock(TournamentRepository.class);
        when(tournaments.lockById(ID)).thenReturn(Optional.of(tournament));
        when(tournaments.save(any())).thenAnswer(call -> call.getArgument(0));

        // In-memory entry table; joinedAt is advanced so ordering is stable.
        TournamentEntryRepository entries = mock(TournamentEntryRepository.class);
        when(entries.findAllByTournamentIdOrderByJoinedAtAscIdAsc(anyLong())).thenAnswer(call -> List.copyOf(stored));
        when(entries.findByTournamentIdAndUserId(anyLong(), anyString()))
                .thenAnswer(call -> stored.stream()
                        .filter(entry -> entry.getUserId().equals(call.getArgument(1)))
                        .findFirst());
        when(entries.save(any())).thenAnswer(call -> {
            TournamentEntry entry = call.getArgument(0);
            if (entry.getId() == 0) {
                entry.setId(nextEntryId++);
                entry.setJoinedAt(clock = clock.plusSeconds(1));
                stored.add(entry);
            }
            return entry;
        });
        doAnswer(call -> stored.remove(call.<TournamentEntry>getArgument(0)))
                .when(entries)
                .delete(any());

        service = new TournamentService(
                tournaments,
                entries,
                mock(TournamentSignupPostRepository.class),
                mock(TournamentGameMasterRepository.class));
    }

    @Test
    void joinsPastTheTargetGoToTheWaitlistAndClosedPoolsRefuse() {
        assertThat(service.join(ID, "a").outcome()).isEqualTo(JoinOutcome.JOINED);
        assertThat(service.join(ID, "b").outcome()).isEqualTo(JoinOutcome.JOINED);
        var third = service.join(ID, "c");
        assertThat(third.outcome()).isEqualTo(JoinOutcome.WAITLISTED);
        assertThat(third.waitlistPosition()).isEqualTo(1);
        assertThat(service.join(ID, "a").outcome()).isEqualTo(JoinOutcome.ALREADY_JOINED);

        tournament.setStatus(TournamentStatus.CLOSED);
        assertThat(service.join(ID, "d").outcome()).isEqualTo(JoinOutcome.CLOSED);
    }

    @Test
    void leavingPromotesFirstWaitlistedIntoTheSameGroup() {
        service.join(ID, "a");
        service.join(ID, "b");
        service.join(ID, "c");
        stored.getFirst().setGroupNumber(3);

        LeaveResult result = service.leave(ID, "a");

        assertThat(result.wasActive()).isTrue();
        assertThat(result.promotedUserId()).isEqualTo("c");
        TournamentEntry promoted = stored.stream()
                .filter(entry -> "c".equals(entry.getUserId()))
                .findFirst()
                .orElseThrow();
        assertThat(promoted.isWaitlisted()).isFalse();
        assertThat(promoted.getGroupNumber()).isEqualTo(3);
    }

    @Test
    void leavingIsRefusedOnceGamesAreBeingCreated() {
        service.join(ID, "a");
        tournament.setStatus(TournamentStatus.CREATING);

        assertThat(service.leave(ID, "a").refusal()).isNotNull();
        assertThat(stored).hasSize(1);
    }

    @Test
    void aPartialRunStaysCreatingAndArchiveIsNeverUndone() {
        seat("a", 1, "pbd1");
        seat("b", 2, null);
        tournament.setStatus(TournamentStatus.GROUPED);

        assertThat(service.beginCreating(ID)).isTrue();
        assertThat(service.finishCreating(ID).orElseThrow().getStatus()).isEqualTo(TournamentStatus.CREATING);
        // A stopped run can be continued.
        assertThat(service.beginCreating(ID)).isTrue();

        tournament.setStatus(TournamentStatus.ARCHIVED);
        assertThat(service.finishCreating(ID).orElseThrow().getStatus()).isEqualTo(TournamentStatus.ARCHIVED);
        assertThat(service.beginCreating(ID)).isFalse();
    }

    @Test
    void afterAPartialRunOnlyPlayersWithoutAGameCanBeSwappedOrReplaced() {
        seat("a", 1, "pbd1");
        seat("b", 2, null);
        seat("c", 2, null);
        tournament.setStatus(TournamentStatus.CREATING);

        assertThat(service.swap(ID, "a", "b", () -> false)).isPresent();
        assertThat(service.swap(ID, "b", "c", () -> true)).isPresent();
        assertThat(service.swap(ID, "b", "c", () -> false)).isEmpty();
        assertThat(service.saveGroups(ID, Map.of(), 1)).isPresent();

        // Replacing needs someone on the waitlist; bulk add puts newcomers there while games exist.
        assertThat(service.removeByAdmin(ID, "b", () -> false).refusal()).isNotNull();
        var added = service.bulkAdd(ID, List.of("d"), () -> false);
        assertThat(added.waitlisted()).containsExactly("d");
        LeaveResult removed = service.removeByAdmin(ID, "b", () -> false);
        assertThat(removed.promotedUserId()).isEqualTo("d");
        assertThat(entry("d").getGroupNumber()).isEqualTo(2);
        assertThat(service.removeByAdmin(ID, "a", () -> false).refusal()).contains("pbd1");
    }

    @Test
    void withdrawingDoesNotPromotePastTheTargetSize() {
        tournament.setTargetSize(1);
        service.bulkAdd(ID, List.of("a", "b"), () -> false);
        service.join(ID, "c");

        assertThat(service.leave(ID, "a").promotedUserId()).isNull();
        assertThat(entry("c").isWaitlisted()).isTrue();
    }

    private void seat(String userId, int group, String gameName) {
        TournamentEntry entry = new TournamentEntry(ID, userId, clock, false, false);
        entry.setGroupNumber(group);
        entry.setGameName(gameName);
        stored.add(entry);
        entry.setId(nextEntryId++);
    }

    private TournamentEntry entry(String userId) {
        return stored.stream()
                .filter(entry -> entry.getUserId().equals(userId))
                .findFirst()
                .orElseThrow();
    }
}
