package ti4.spring.service.techsummary;

import org.springframework.data.jpa.repository.JpaRepository;

interface TechSummaryEntityRepository extends JpaRepository<TechSummaryEntity, String> {}
