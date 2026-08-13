package ti4.spring.service.statistics.overrule;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @deprecated read-only accessor for the legacy {@code overrule_choice} table so {@link
 *     OverruleChoiceMigrationRunner} can drain it into {@link OverrulePlayEntity}. Delete along
 *     with {@link OverruleChoiceEntity} once the table has been dropped everywhere.
 */
@Deprecated
interface OverruleChoiceRepository extends JpaRepository<OverruleChoiceEntity, Long> {}
