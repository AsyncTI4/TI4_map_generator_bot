package ti4.listeners;

import org.springframework.data.repository.CrudRepository;

interface BotMessageCacheRepository extends CrudRepository<BotMessageRecord, Long> {

    void deleteByCreatedAtEpochMillisLessThan(long cutoffEpochMillis);
}
