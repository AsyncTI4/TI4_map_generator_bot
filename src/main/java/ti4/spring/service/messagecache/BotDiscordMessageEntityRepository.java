package ti4.spring.service.messagecache;

import org.springframework.data.repository.CrudRepository;

interface BotDiscordMessageEntityRepository extends CrudRepository<BotDiscordMessageEntity, Long> {
    void deleteByCreatedAtEpochMillisLessThan(long cutoffEpochMillis);
}
