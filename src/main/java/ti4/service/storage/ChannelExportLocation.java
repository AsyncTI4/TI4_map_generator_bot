package ti4.service.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

public record ChannelExportLocation(Path exportsRoot, String channelId, String exportId) {

    private static final int MAX_LOG_TAIL_BYTES = 1200;
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;?0-9]*[A-Za-z]");

    public Path directory() {
        return exportsRoot.resolve(channelId);
    }

    public Path workDirectory() {
        return directory().resolve(exportId);
    }

    public Path archive() {
        return directory().resolve(exportId + ".tar.gz");
    }

    public Path doneMarker() {
        return directory().resolve(exportId + ".done");
    }

    public Path failedMarker() {
        return directory().resolve(exportId + ".failed");
    }

    public Path log() {
        return directory().resolve(exportId + ".log");
    }

    public String displayPath(Path file) {
        return exportsRoot.getFileName() + "/" + channelId + "/" + file.getFileName();
    }

    public ChannelExportStatus status() {
        if (Files.exists(failedMarker())) return ChannelExportStatus.FAILED;
        if (Files.exists(doneMarker())) return ChannelExportStatus.DONE;
        if (Files.exists(log()) || Files.exists(workDirectory())) return ChannelExportStatus.RUNNING;
        return ChannelExportStatus.NOT_STARTED;
    }

    public String failureReason() throws IOException {
        return Files.readString(failedMarker(), StandardCharsets.UTF_8).trim();
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
}
