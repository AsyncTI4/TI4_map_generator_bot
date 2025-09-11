package ti4.spring.service.tournamentwinner;

import org.springframework.data.jpa.repository.JpaRepository;

interface TournamentWinnerRepository extends JpaRepository<TournamentWinner, Long> {

    boolean existsByUserId(String userId);

    void deleteByUserIdAndTourneyName(String userId, String tourneyName);
}
