package ti4.spring.service.persistence;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameEntityPersistenceService {

    private final GameEntityRepository gameEntityRepository;
    private final PlayerEntityRepository playerEntityRepository;
    private final TitleEntityRepository titleEntityRepository;
    private final UserEntityRepository userEntityRepository;

    @Transactional
    public void replace(GameEntitySnapshot snapshot) {
        if (hasNewerVersion(snapshot.game().getGameName(), snapshot.game().getSyncVersion())) return;
        upsertUsers(snapshot.users());
        deleteGameRows(snapshot.game().getGameName());
        gameEntityRepository.save(snapshot.game());
        titleEntityRepository.saveAll(snapshot.titles());
    }

    @Transactional
    public void delete(String gameName) {
        deleteGameRows(gameName);
    }

    @Transactional
    public void deleteUnlessNewer(String gameName, long syncVersion) {
        if (hasNewerVersion(gameName, syncVersion)) return;
        deleteGameRows(gameName);
    }

    private boolean hasNewerVersion(String gameName, Long syncVersion) {
        if (syncVersion == null) return false;
        return gameEntityRepository
                .findSyncVersionByGameName(gameName)
                .filter(storedVersion -> storedVersion > syncVersion)
                .isPresent();
    }

    private void upsertUsers(List<UserEntity> users) {
        List<UserEntity> usersToSave = users.stream()
                .filter(user ->
                        !GameEntityMapper.hasUnknownName(user) || !userEntityRepository.existsById(user.getId()))
                .toList();
        userEntityRepository.saveAll(usersToSave);
    }

    private void deleteGameRows(String gameName) {
        titleEntityRepository.deleteByGameName(gameName);
        playerEntityRepository.deleteByGameName(gameName);
        gameEntityRepository.deleteByGameName(gameName);
    }
}
