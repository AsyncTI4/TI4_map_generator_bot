package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TitleEntityRepository extends JpaRepository<TitleEntity, Long> {

    void deleteByGame_GameName(String gameName);

    boolean existsByUser_Id(String userId);

    @Query("SELECT DISTINCT t.user.id FROM TitleEntity t WHERE t.game.gameName = :gameName")
    List<String> findDistinctUserIdsByGameName(@Param("gameName") String gameName);

    @Query("SELECT t FROM TitleEntity t JOIN FETCH t.user")
    List<TitleEntity> findAllWithUsers();
}
