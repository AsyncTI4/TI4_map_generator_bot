package ti4.spring.service.tournament;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TournamentSignupPostRepository extends JpaRepository<TournamentSignupPost, Long> {

    List<TournamentSignupPost> findAllByTournamentIdOrderByIdAsc(long tournamentId);
}
