package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TitleEntityRepository extends JpaRepository<TitleEntity, Long> {

    @Query("SELECT t FROM TitleEntity t JOIN FETCH t.user JOIN FETCH t.game")
    List<TitleEntity> findAllWithUsersAndGames();

    @Query("SELECT t FROM TitleEntity t JOIN FETCH t.user")
    List<TitleEntity> findAllWithUsers();

    @Modifying
    @Query("DELETE FROM TitleEntity t WHERE t.game.gameName = :gameName")
    void deleteByGameName(@Param("gameName") String gameName);
}
