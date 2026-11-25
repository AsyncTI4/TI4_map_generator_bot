package ti4.spring.service.autoping;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AutoPingEntityRepository extends JpaRepository<AutoPingEntity, String> {

    @Query("select autoPing.gameName from AutoPingEntity autoPing")
    List<String> findAllGameNames();
}
