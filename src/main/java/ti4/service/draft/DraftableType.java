package ti4.service.draft;

import java.util.Objects;

public class DraftableType {
    private final String name;

    private DraftableType(String name) {
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DraftableType that = (DraftableType) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }

    public static DraftableType of(String name) {
        return new DraftableType(name);
    }
}
