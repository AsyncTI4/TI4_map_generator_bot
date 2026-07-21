package ti4.spring.service.tournamentwinner;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface TournamentWinnerRepository extends JpaRepository<TournamentWinner, Long> {

    boolean existsByUserId(String userId);

    @Transactional
    @Modifying
    @Query("delete from TournamentWinner winner where winner.userId = :userId and winner.tourneyName = :tourneyName")
    void deleteWinner(@Param("userId") String userId, @Param("tourneyName") String tourneyName);
}
