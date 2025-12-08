package ti4.spring.service.messagecache;

import org.springframework.data.repository.CrudRepository;

interface BotMessageCacheRepository extends CrudRepository<BotMessageRecord, Long> {
    void deleteByCreatedAtEpochMillisLessThan(long cutoffEpochMillis);
}
