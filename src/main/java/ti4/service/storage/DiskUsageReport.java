package ti4.service.storage;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DiskUsageReport {

    private static final String SAVE_FILES_DIRECTORY = "maps";
    private static final String UNDO_DIRECTORY = "undo";
    private static final String ROOT_FILES_LABEL = "(files in storage root)";

    public record Usage(String label, long bytes, long files) {}

    public static String build(Path storageRoot, @Nullable Path tempDirectory) throws IOException {
        List<Usage> storageUsages = measureStorage(storageRoot);
        StringBuilder report =
                new StringBuilder("**Disk usage** of `").append(storageRoot).append("`\n");
        report.append(describeVolume(Files.getFileStore(storageRoot))).append("\n```\n");
        report.append(formatTable(storageUsages, total("total", storageUsages)));
        if (tempDirectory != null && Files.isDirectory(tempDirectory) && !tempDirectory.startsWith(storageRoot)) {
            report.append(formatRow(measure("temp: " + tempDirectory, tempDirectory, null)));
        }
        return report.append("```").toString();
    }

    public static List<Usage> measureStorage(Path storageRoot) throws IOException {
        List<Usage> usages = new ArrayList<>();
        long rootFileBytes = 0;
        long rootFileCount = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(storageRoot)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    usages.addAll(measureTopLevelDirectory(entry));
                } else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    rootFileBytes += Files.size(entry);
                    rootFileCount++;
                }
            }
        }
        if (rootFileCount > 0) {
            usages.add(new Usage(ROOT_FILES_LABEL, rootFileBytes, rootFileCount));
        }
        usages.sort(Comparator.comparingLong(Usage::bytes).reversed().thenComparing(Usage::label));
        return usages;
    }

    private static List<Usage> measureTopLevelDirectory(Path directory) throws IOException {
        String name = directory.getFileName().toString();
        Path undoDirectory = directory.resolve(UNDO_DIRECTORY);
        if (!SAVE_FILES_DIRECTORY.equals(name) || !Files.isDirectory(undoDirectory)) {
            return List.of(measure(name + "/", directory, null));
        }
        return List.of(
                measure(name + "/ (save files)", directory, undoDirectory),
                measure(name + "/" + UNDO_DIRECTORY + "/", undoDirectory, null));
    }

    private static Usage measure(String label, Path directory, @Nullable Path excludedDirectory) throws IOException {
        long[] totals = new long[2];
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                return dir.equals(excludedDirectory) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    totals[0] += attributes.size();
                    totals[1]++;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        return new Usage(label, totals[0], totals[1]);
    }

    private static Usage total(String label, List<Usage> usages) {
        long bytes = usages.stream().mapToLong(Usage::bytes).sum();
        long files = usages.stream().mapToLong(Usage::files).sum();
        return new Usage(label, bytes, files);
    }

    private static String describeVolume(FileStore store) throws IOException {
        long totalSpace = store.getTotalSpace();
        long freeSpace = store.getUsableSpace();
        long usedSpace = totalSpace - store.getUnallocatedSpace();
        int usedPercent = totalSpace == 0 ? 0 : (int) Math.round(100.0 * usedSpace / totalSpace);
        return "Volume: " + ByteSizes.format(usedSpace) + " used of " + ByteSizes.format(totalSpace) + " ("
                + usedPercent + "%), " + ByteSizes.format(freeSpace) + " free";
    }

    private static String formatTable(List<Usage> usages, Usage total) {
        StringBuilder table = new StringBuilder(formatColumns("directory", "size", "files"));
        usages.forEach(usage -> table.append(formatRow(usage)));
        return table.append(formatRow(total)).toString();
    }

    private static String formatRow(Usage usage) {
        String files = String.format(Locale.ROOT, "%,d", usage.files());
        return formatColumns(usage.label(), ByteSizes.format(usage.bytes()), files);
    }

    private static String formatColumns(String label, String size, String files) {
        return String.format(Locale.ROOT, "%-28s %10s %12s", label, size, files) + "\n";
    }
}
