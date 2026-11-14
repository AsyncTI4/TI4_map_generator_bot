package ti4.spring.service.tournament;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TournamentRepository extends JpaRepository<Tournament, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Tournament t where t.id = :id")
    Optional<Tournament> lockById(@Param("id") long id);

    List<Tournament> findAllByStatusNotOrderByIdAsc(TournamentStatus status);

    boolean existsByParticipantRoleIdAndStatusNotAndIdNot(String roleId, TournamentStatus status, long id);
}
