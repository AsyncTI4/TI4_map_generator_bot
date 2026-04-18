package ti4.spring.service.deploy;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActiveLeaseRepository extends JpaRepository<ActiveLeaseEntity, String> {}
