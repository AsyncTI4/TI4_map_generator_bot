package ti4.spring.service.persistence;

import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public record GameFileRevision(boolean exists, @Nullable String revisionKey) {

    public static GameFileRevision missing() {
        return new GameFileRevision(false, null);
    }

    public static GameFileRevision of(@Nullable String revisionKey) {
        return new GameFileRevision(true, revisionKey);
    }

    boolean matches(@Nullable String snapshotRevisionKey) {
        if (!exists) return false;
        if (StringUtils.isBlank(revisionKey)) return StringUtils.isBlank(snapshotRevisionKey);
        return revisionKey.equals(snapshotRevisionKey);
    }
}
