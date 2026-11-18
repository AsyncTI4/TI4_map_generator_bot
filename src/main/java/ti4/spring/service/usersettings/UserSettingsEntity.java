package ti4.spring.service.usersettings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "user_settings")
class UserSettingsEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "settings_json", nullable = false, columnDefinition = "TEXT")
    private String settingsJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    UserSettingsEntity(String userId, String settingsJson) {
        this.userId = userId;
        this.settingsJson = settingsJson;
        this.updatedAt = LocalDateTime.now();
    }

    void replaceSettings(String settingsJson) {
        this.settingsJson = settingsJson;
        this.updatedAt = LocalDateTime.now();
    }
}
