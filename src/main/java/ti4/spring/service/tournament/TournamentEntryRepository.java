package ti4.spring.service.tournament;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface TournamentEntryRepository extends JpaRepository<TournamentEntry, Long> {

    List<TournamentEntry> findAllByTournamentIdOrderByJoinedAtAscIdAsc(long tournamentId);

    Optional<TournamentEntry> findByTournamentIdAndUserId(long tournamentId, String userId);
}
