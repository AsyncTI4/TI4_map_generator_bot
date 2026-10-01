package ti4.spring.service.statistics.overrule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OverruleChoiceRepository extends JpaRepository<OverruleChoiceEntity, Long> {

    @Modifying
    @Query(
            value = "UPDATE overrule_choice SET count = count + :delta "
                    + "WHERE game_name = :gameName AND strategy_card = :strategyCard",
            nativeQuery = true)
    int incrementExistingBy(
            @Param("gameName") String gameName, @Param("strategyCard") String strategyCard, @Param("delta") long delta);

    @Modifying
    @Query(
            value =
                    "INSERT INTO overrule_choice (game_name, strategy_card, count) "
                            + "SELECT :gameName, :strategyCard, :delta "
                            + "WHERE NOT EXISTS (SELECT 1 FROM overrule_choice WHERE game_name = :gameName AND strategy_card = :strategyCard)",
            nativeQuery = true)
    int insertCount(
            @Param("gameName") String gameName, @Param("strategyCard") String strategyCard, @Param("delta") long delta);
}
