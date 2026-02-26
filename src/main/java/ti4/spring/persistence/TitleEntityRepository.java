package ti4.spring.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TitleEntityRepository extends JpaRepository<TitleEntity, Long> {

    @Query("SELECT t FROM TitleEntity t JOIN FETCH t.user")
    List<TitleEntity> findAllWithUsers();
}
