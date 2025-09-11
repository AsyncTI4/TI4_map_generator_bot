package ti4.spring.service.tournamentwinner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface TournamentWinnerRepository extends JpaRepository<TournamentWinner, Long> {

    boolean existsByUserId(String userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM TournamentWinner t WHERE t.userId = :userId AND t.tourneyName = :tourneyName")
    int deleteByUserIdAndTourneyName(String userId, String tourneyName);
}
