package ti4.spring.service.messagecache;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

interface BotDiscordMessageEntityRepository extends CrudRepository<BotDiscordMessageEntity, Long> {
    @Modifying
    @Query("delete from BotDiscordMessageEntity message where message.createdAtEpochMillis < :cutoffEpochMillis")
    int deleteByCreatedAtEpochMillisLessThan(@Param("cutoffEpochMillis") long cutoffEpochMillis);
}
