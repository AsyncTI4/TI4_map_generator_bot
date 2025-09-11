package ti4.spring.api.image;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "map_image_data")
class MapImageData {
    @Id
    private String gameName;

    private String latestMapImageName;
}
