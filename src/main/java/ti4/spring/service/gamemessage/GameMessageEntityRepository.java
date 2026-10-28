package ti4.spring.service.gamemessage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ti4.message.GameMessageType;

interface GameMessageEntityRepository extends JpaRepository<GameMessageEntity, Long> {

    List<GameMessageEntity> findByGameNameOrderByIdAsc(String gameName);

    List<GameMessageEntity> findByGameNameAndTypeOrderByIdAsc(String gameName, GameMessageType type);

    List<GameMessageEntity> findByTypeOrderByIdAsc(GameMessageType type);

    @Modifying
    @Query("delete from GameMessageEntity message where message.gameName = :gameName")
    int deleteByGameName(@Param("gameName") String gameName);

    @Modifying
    @Query("delete from GameMessageEntity message"
            + " where message.gameName = :gameName and message.gameSaveTime > :gameSaveTime")
    int deleteSavedAfter(@Param("gameName") String gameName, @Param("gameSaveTime") long gameSaveTime);

    @Modifying
    @Query("delete from GameMessageEntity message"
            + " where message.gameName = :gameName and message.messageId = :messageId")
    int deleteByGameNameAndMessageId(@Param("gameName") String gameName, @Param("messageId") String messageId);
}
