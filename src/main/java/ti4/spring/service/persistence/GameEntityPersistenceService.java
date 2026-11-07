package ti4.spring.service.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ti4.spring.context.SpringContext;

@Service
@RequiredArgsConstructor
public class GameEntityPersistenceService {

    private final GameEntityRepository gameEntityRepository;
    private final PlayerEntityRepository playerEntityRepository;
    private final TitleEntityRepository titleEntityRepository;
    private final UserEntityRepository userEntityRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void replace(GameEntitySnapshot snapshot) {
        Map<String, UserEntity> users =
                saveUsers(snapshot.users(), (existing, incoming) -> !GameEntityMapper.hasUnknownName(incoming));
        deleteGameRows(snapshot.game().getGameName());
        insertGameRows(snapshot, users);
    }

    @Transactional
    public void restore(GameEntitySnapshot snapshot) {
        Map<String, UserEntity> users = saveUsers(
                snapshot.users(),
                (existing, incoming) ->
                        GameEntityMapper.hasUnknownName(existing) && !GameEntityMapper.hasUnknownName(incoming));
        deleteGameRows(snapshot.game().getGameName());
        insertGameRows(snapshot, users);
    }

    @Transactional
    public boolean replaceIfMatchesGameFile(
            GameEntitySnapshot snapshot, Supplier<GameFileRevision> currentFileRevision) {
        if (!lockAndCheckMatchesGameFile(snapshot, currentFileRevision)) return false;
        replace(snapshot);
        return true;
    }

    @Transactional
    public boolean restoreIfMatchesGameFile(
            GameEntitySnapshot snapshot, Supplier<GameFileRevision> currentFileRevision) {
        if (!lockAndCheckMatchesGameFile(snapshot, currentFileRevision)) return false;
        restore(snapshot);
        return true;
    }

    @Transactional
    public void delete(String gameName) {
        deleteGameRows(gameName);
    }

    @Transactional
    public boolean deleteIfGameFileMissing(String gameName, Supplier<GameFileRevision> currentFileRevision) {
        lockGame(gameName);
        if (currentFileRevision.get().exists()) return false;
        deleteGameRows(gameName);
        return true;
    }

    private boolean lockAndCheckMatchesGameFile(
            GameEntitySnapshot snapshot, Supplier<GameFileRevision> currentFileRevision) {
        GameEntity game = snapshot.game();
        lockGame(game.getGameName());
        return currentFileRevision.get().matches(game.getRevisionKey()) && !rowHasNewerMapping(game);
    }

    private void lockGame(String gameName) {
        entityManager
                .createNativeQuery("select 1 from pg_advisory_xact_lock(hashtext(?1))")
                .setParameter(1, gameName)
                .getSingleResult();
    }

    private boolean rowHasNewerMapping(GameEntity incoming) {
        List<?> storedVersions = entityManager
                .createNativeQuery("select mapping_version from game where game_name = ?1")
                .setParameter(1, incoming.getGameName())
                .getResultList();
        if (storedVersions.isEmpty() || storedVersions.getFirst() == null) return false;
        int storedVersion = ((Number) storedVersions.getFirst()).intValue();
        int incomingVersion = Objects.requireNonNullElse(incoming.getMappingVersion(), 0);
        return storedVersion > incomingVersion;
    }

    private Map<String, UserEntity> saveUsers(
            List<UserEntity> users, BiPredicate<UserEntity, UserEntity> shouldReplaceExistingName) {
        Map<String, UserEntity> managedUsers = new HashMap<>();
        userEntityRepository
                .findAllById(users.stream().map(UserEntity::getId).toList())
                .forEach(user -> managedUsers.put(user.getId(), user));
        for (UserEntity user : users) {
            UserEntity existing = managedUsers.get(user.getId());
            if (existing == null) {
                entityManager.persist(user);
                managedUsers.put(user.getId(), user);
            } else if (shouldReplaceExistingName.test(existing, user)) {
                existing.setName(user.getName());
            }
        }
        return managedUsers;
    }

    private void insertGameRows(GameEntitySnapshot snapshot, Map<String, UserEntity> managedUsers) {
        GameEntity game = snapshot.game();
        for (PlayerEntity player : game.getPlayers()) {
            player.setStatsUser(managed(player.getStatsUser(), managedUsers));
            player.setPlayingUser(managed(player.getPlayingUser(), managedUsers));
        }
        entityManager.persist(game);
        for (TitleEntity title : snapshot.titles()) {
            title.setGame(game);
            title.setUser(managed(title.getUser(), managedUsers));
            entityManager.persist(title);
        }
    }

    private UserEntity managed(UserEntity user, Map<String, UserEntity> managedUsers) {
        if (user == null) return null;
        UserEntity managedUser = managedUsers.get(user.getId());
        return managedUser != null ? managedUser : entityManager.getReference(UserEntity.class, user.getId());
    }

    private void deleteGameRows(String gameName) {
        titleEntityRepository.deleteByGameName(gameName);
        playerEntityRepository.deleteByGameName(gameName);
        gameEntityRepository.deleteByGameName(gameName);
    }

    public static GameEntityPersistenceService getBean() {
        return SpringContext.getBean(GameEntityPersistenceService.class);
    }
}
