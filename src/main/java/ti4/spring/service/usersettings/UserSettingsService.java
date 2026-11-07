package ti4.spring.service.usersettings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ti4.json.JsonMapperManager;
import ti4.settings.users.UserSettings;
import ti4.spring.context.SpringContext;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private static final JsonMapper JSON_MAPPER = JsonMapperManager.basic();

    private final UserSettingsEntityRepository repository;
    private final TransactionTemplate transactionTemplate;

    public static UserSettingsService getBean() {
        return SpringContext.getBean(UserSettingsService.class);
    }

    public Optional<UserSettings> find(String userId) {
        return repository.findById(userId).map(UserSettingsService::toUserSettings);
    }

    public List<UserSettings> findAllById(Collection<String> userIds) {
        return repository.findAllById(userIds).stream()
                .map(UserSettingsService::toUserSettings)
                .toList();
    }

    public List<UserSettings> findAll() {
        return repository.findAll().stream()
                .map(UserSettingsService::toUserSettings)
                .toList();
    }

    public void save(UserSettings userSettings) {
        transactionTemplate.executeWithoutResult(_ -> upsert(userSettings));
    }

    public void update(String userId, Supplier<UserSettings> whenAbsent, Consumer<UserSettings> change) {
        transactionTemplate.executeWithoutResult(_ -> {
            UserSettings userSettings = find(userId).orElseGet(whenAbsent);
            change.accept(userSettings);
            upsert(userSettings);
        });
    }

    public void updateAll(
            Map<String, Consumer<UserSettings>> changesByUserId, Function<String, UserSettings> whenAbsent) {
        if (changesByUserId.isEmpty()) return;
        transactionTemplate.executeWithoutResult(_ -> {
            Map<String, UserSettingsEntity> existingByUserId = new HashMap<>();
            repository
                    .findAllById(changesByUserId.keySet())
                    .forEach(entity -> existingByUserId.put(entity.getUserId(), entity));
            List<UserSettingsEntity> created = new ArrayList<>();
            changesByUserId.forEach((userId, change) -> {
                UserSettingsEntity entity = existingByUserId.get(userId);
                UserSettings userSettings = entity == null ? whenAbsent.apply(userId) : toUserSettings(entity);
                change.accept(userSettings);
                String json = toJson(userSettings);
                if (entity == null) {
                    created.add(new UserSettingsEntity(userId, json));
                } else {
                    entity.replaceSettings(json);
                }
            });
            repository.saveAll(created);
        });
    }

    private void upsert(UserSettings userSettings) {
        String json = toJson(userSettings);
        repository
                .findById(userSettings.getUserId())
                .ifPresentOrElse(
                        entity -> entity.replaceSettings(json),
                        () -> repository.save(new UserSettingsEntity(userSettings.getUserId(), json)));
    }

    private static String toJson(UserSettings userSettings) {
        return JSON_MAPPER.writeValueAsString(userSettings);
    }

    private static UserSettings toUserSettings(UserSettingsEntity entity) {
        UserSettings userSettings = JSON_MAPPER.readValue(entity.getSettingsJson(), UserSettings.class);
        userSettings.setUserId(entity.getUserId());
        return userSettings;
    }
}
