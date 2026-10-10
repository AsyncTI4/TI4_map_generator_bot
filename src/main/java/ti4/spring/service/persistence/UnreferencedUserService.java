package ti4.spring.service.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class UnreferencedUserService {

    private final UserEntityRepository userEntityRepository;

    @Transactional(readOnly = true)
    public List<String> findUnreferencedUserIds() {
        return userEntityRepository.findUnreferencedUserIds();
    }

    @Transactional
    public int deleteUnreferencedUsers(List<String> userIds) {
        if (userIds.isEmpty()) return 0;
        return userEntityRepository.deleteUnreferencedUsers(userIds);
    }

    public static UnreferencedUserService getBean() {
        return SpringContext.getBean(UnreferencedUserService.class);
    }
}
