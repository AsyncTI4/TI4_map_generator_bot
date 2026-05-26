package ti4.spring.service.usage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "slash_command_count", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "date"}))
public class SlashCommandCountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "count", nullable = false)
    private long count;

    public SlashCommandCountEntity(String name, LocalDate date, long count) {
        this.name = name;
        this.date = date;
        this.count = count;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        SlashCommandCountEntity that = (SlashCommandCountEntity) other;
        return Objects.equals(name, that.name) && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, date);
    }
}
