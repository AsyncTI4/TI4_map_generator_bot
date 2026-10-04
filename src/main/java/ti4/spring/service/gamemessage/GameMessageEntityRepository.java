package ti4.spring.service.gamemessage;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ti4.message.GameMessageType;

interface GameMessageEntityRepository extends JpaRepository<GameMessageEntity, Long> {

    @Query(
            value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:gameName))) as game_lock",
            nativeQuery = true)
    long lockGameUntilTransactionEnds(@Param("gameName") String gameName);

    List<GameMessageEntity> findByGameNameOrderByIdAsc(String gameName);

    List<GameMessageEntity> findByGameNameAndTypeOrderByIdAsc(String gameName, GameMessageType type);

    List<GameMessageEntity> findByTypeOrderByIdAsc(GameMessageType type);

    @Modifying
    @Query("delete from GameMessageEntity message where message.gameName in :gameNames")
    int deleteByGameNames(@Param("gameNames") Collection<String> gameNames);

    @Modifying
    @Query("delete from GameMessageEntity message"
            + " where message.gameName = :gameName and message.gameSaveTime > :gameSaveTime")
    int deleteSavedAfter(@Param("gameName") String gameName, @Param("gameSaveTime") long gameSaveTime);

    @Modifying
    @Query("delete from GameMessageEntity message"
            + " where message.gameName = :gameName and message.messageId = :messageId")
    int deleteByGameNameAndMessageId(@Param("gameName") String gameName, @Param("messageId") String messageId);
}
