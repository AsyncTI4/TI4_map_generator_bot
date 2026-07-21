package ti4.spring.service.messagecache;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface BotDiscordMessageEntityRepository extends CrudRepository<BotDiscordMessageEntity, Long> {

    @Transactional
    @Modifying
    @Query(value = """
                    insert into bot_discord_message (message_id, content, created_at_epoch_millis)
                    values (:messageId, :content, :createdAt)
                    on conflict (message_id) do update set
                        content = excluded.content,
                        created_at_epoch_millis = excluded.created_at_epoch_millis
                    """, nativeQuery = true)
    void upsert(
            @Param("messageId") long messageId,
            @Param("content") String content,
            @Param("createdAt") long createdAtEpochMillis);

    @Modifying
    @Query("delete from BotDiscordMessageEntity message where message.createdAtEpochMillis < :cutoff")
    int deleteExpired(@Param("cutoff") long cutoffEpochMillis);

    @Transactional
    @Modifying
    @Query("delete from BotDiscordMessageEntity message where message.messageId = :messageId")
    void deleteMessage(@Param("messageId") long messageId);
}
