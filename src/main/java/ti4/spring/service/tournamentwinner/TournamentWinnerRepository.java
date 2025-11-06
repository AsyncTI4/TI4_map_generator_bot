package ti4.spring.service.tournamentwinner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface TournamentWinnerRepository extends JpaRepository<TournamentWinner, Long> {

    boolean existsByUserId(String userId);

    @Transactional
    void deleteByUserIdAndTourneyName(String userId, String tourneyName);
}
