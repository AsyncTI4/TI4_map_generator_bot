package ti4.service.storage;

public enum ChannelExportStatus {
    NOT_STARTED,
    RUNNING,
    CANCELLED,
    PAUSED,
    FAILED,
    DONE;

    public boolean isResumable() {
        return this == CANCELLED || this == PAUSED || this == FAILED;
    }
}
