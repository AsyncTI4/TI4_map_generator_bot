package ti4.spring.api.image;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

interface PlayerMapImageDataRepository extends CrudRepository<PlayerMapImageData, Long> {

    Optional<PlayerMapImageData> findByGameNameAndPlayerId(String gameName, String playerId);
}
