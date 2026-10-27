package ti4.spring.service.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserEntityRepository extends JpaRepository<UserEntity, String> {

    String UNREFERENCED_USER_CONDITION = """
            NOT EXISTS (SELECT 1 FROM PlayerEntity p WHERE p.user = u)
            AND NOT EXISTS (SELECT 1 FROM TitleEntity t WHERE t.user = u)
            AND NOT EXISTS (SELECT 1 FROM StandaloneTitleEntity s WHERE s.user = u)
            """;

    @Query("SELECT u.id FROM UserEntity u WHERE " + UNREFERENCED_USER_CONDITION)
    List<String> findUnreferencedUserIds();

    @Modifying
    @Query("DELETE FROM UserEntity u WHERE u.id IN :userIds AND " + UNREFERENCED_USER_CONDITION)
    int deleteUnreferencedUsers(@Param("userIds") List<String> userIds);
}
