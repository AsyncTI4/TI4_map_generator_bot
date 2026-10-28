package ti4.spring.service.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
