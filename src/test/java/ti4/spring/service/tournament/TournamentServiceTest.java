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
}
