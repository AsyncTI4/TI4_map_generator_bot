package ti4.service.storage;

public enum ChannelExportStatus {
    NOT_STARTED,
    RUNNING,
    CANCELLED,
    FAILED,
    DONE;

    public boolean isResumable() {
        return this == CANCELLED || this == FAILED;
    }
}
