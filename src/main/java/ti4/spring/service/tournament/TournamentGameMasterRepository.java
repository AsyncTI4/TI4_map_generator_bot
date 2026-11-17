package ti4.spring.service.tournament;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface TournamentGameMasterRepository extends JpaRepository<TournamentGameMaster, Long> {

    List<TournamentGameMaster> findAllByTournamentIdOrderByIdAsc(long tournamentId);

    Optional<TournamentGameMaster> findByTournamentIdAndUserId(long tournamentId, String userId);
}
