package ti4.spring.api.overlay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "map_overlay_data")
class MapOverlayData {
    @Id
    private String gameName;

    @Column(name = "overlays_json", nullable = false, columnDefinition = "TEXT")
    private String overlaysJson;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
