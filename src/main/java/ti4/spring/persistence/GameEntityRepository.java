package ti4.spring.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEntityRepository extends JpaRepository<GameEntity, String> {}
