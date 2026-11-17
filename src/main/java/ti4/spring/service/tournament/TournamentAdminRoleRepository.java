package ti4.spring.service.tournament;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface TournamentAdminRoleRepository extends JpaRepository<TournamentAdminRole, Long> {

    Optional<TournamentAdminRole> findByRoleId(String roleId);
}
