package ti4.spring.service.autoping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.model.metadata.AutoPingMetadataManager.AutoPing;

@Data
@NoArgsConstructor
@Entity
@Table(name = "game_auto_ping")
class AutoPingEntity {

    @Id
    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "last_ping_epoch_milliseconds", nullable = false)
    private long lastPingEpochMilliseconds;

    @Column(name = "ping_count", nullable = false)
    private int pingCount;

    @Column(name = "quick_ping", nullable = false)
    private boolean quickPing;

    AutoPingEntity(String gameName, AutoPing autoPing) {
        this.gameName = gameName;
        apply(autoPing);
    }

    void apply(AutoPing autoPing) {
        lastPingEpochMilliseconds = autoPing.lastPingTimeEpochMilliseconds();
        pingCount = autoPing.pingCount();
        quickPing = autoPing.quickPing();
    }

    AutoPing toAutoPing() {
        return new AutoPing(lastPingEpochMilliseconds, pingCount, quickPing);
    }
}
