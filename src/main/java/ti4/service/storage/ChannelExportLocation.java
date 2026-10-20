package ti4.service.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record ChannelExportLocation(Path exportsRoot, String channelId, String exportId) {

    private static final int MAX_LOG_TAIL_BYTES = 1200;
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;?0-9]*[A-Za-z]");
    private static final Pattern EXPORT_ID = Pattern.compile("[0-9A-Za-z_-]+");

    public static boolean isValidExportId(String exportId) {
        return exportId != null && EXPORT_ID.matcher(exportId).matches();
    }

    public static List<ChannelExportLocation> find(Path exportsRoot, String exportId) throws IOException {
        if (!isValidExportId(exportId) || !Files.isDirectory(exportsRoot)) return List.of();
        try (Stream<Path> channelDirectories = Files.list(exportsRoot)) {
            return channelDirectories
                    .filter(Files::isDirectory)
                    .map(directory -> new ChannelExportLocation(
                            exportsRoot, directory.getFileName().toString(), exportId))
                    .filter(ChannelExportLocation::exists)
                    .toList();
        }
    }

    public Path directory() {
        return exportsRoot.resolve(channelId);
    }

    public Path workDirectory() {
        return directory().resolve(exportId);
    }

    public Path archive() {
        return sibling(".tar.gz");
    }

    public Path doneMarker() {
        return sibling(".done");
    }

    public Path failedMarker() {
        return sibling(".failed");
    }

    public Path cancelledMarker() {
        return sibling(".cancelled");
    }

    public Path pausedMarker() {
        return sibling(".paused");
    }

    public Path cancelRequest() {
        return sibling(".cancel");
    }

    public Path runningMarker() {
        return sibling(".running");
    }

    public Path savedRequest() {
        return sibling(".request");
    }

    public Path progress() {
        return sibling(".progress");
    }

    public Path partCount() {
        return sibling(".parts");
    }

    public Path log() {
        return sibling(".log");
    }

    public String displayPath(Path file) {
        return exportsRoot.getFileName() + "/" + channelId + "/" + file.getFileName();
    }

    public boolean exists() {
        return Stream.of(log(), savedRequest(), runningMarker(), failedMarker(), doneMarker())
                .anyMatch(Files::exists);
    }

    public ChannelExportStatus status() {
        if (Files.exists(failedMarker())) return ChannelExportStatus.FAILED;
        if (Files.exists(cancelledMarker())) return ChannelExportStatus.CANCELLED;
        if (Files.exists(pausedMarker())) return ChannelExportStatus.PAUSED;
        if (Files.exists(doneMarker())) return ChannelExportStatus.DONE;
        if (Files.exists(runningMarker())) return ChannelExportStatus.RUNNING;
        return ChannelExportStatus.NOT_STARTED;
    }

    public boolean canResume() {
        return status().isResumable() && Files.exists(savedRequest());
    }

    public boolean isCancelRequested() {
        return Files.exists(cancelRequest());
    }

    public void requestCancel() throws IOException {
        Files.writeString(cancelRequest(), "cancel\n", StandardCharsets.UTF_8);
    }

    public void clearStopMarkers() throws IOException {
        Files.deleteIfExists(failedMarker());
        Files.deleteIfExists(cancelledMarker());
        Files.deleteIfExists(pausedMarker());
        Files.deleteIfExists(cancelRequest());
    }

    public void markFailed(String reason) throws IOException {
        Files.writeString(failedMarker(), reason + "\n", StandardCharsets.UTF_8);
    }

    public Optional<String> progressSummary() throws IOException {
        if (!Files.exists(partCount())) return Optional.empty();
        String total = Files.readString(partCount(), StandardCharsets.UTF_8).trim();
        return Optional.of(finishedPartCount() + " of " + total + " parts");
    }

    private long finishedPartCount() throws IOException {
        if (!Files.exists(progress())) return 0;
        try (Stream<String> lines = Files.lines(progress(), StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank()).count();
        }
    }

    public String stopReason(Path marker) throws IOException {
        return Files.readString(marker, StandardCharsets.UTF_8).trim();
    }

    public String logTail() throws IOException {
        if (!Files.exists(log())) return "";
        try (SeekableByteChannel channel = Files.newByteChannel(log())) {
            long size = channel.size();
            int length = (int) Math.min(size, MAX_LOG_TAIL_BYTES);
            channel.position(size - length);
            ByteBuffer buffer = ByteBuffer.allocate(length);
            while (buffer.hasRemaining() && channel.read(buffer) > 0) {}
            String tail = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
            return ANSI_ESCAPE.matcher(tail).replaceAll("").trim();
        }
    }

    private Path sibling(String suffix) {
        return directory().resolve(exportId + suffix);
    }
}
