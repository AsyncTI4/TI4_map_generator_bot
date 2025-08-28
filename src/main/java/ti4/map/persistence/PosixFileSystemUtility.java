package ti4.map.persistence;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.message.logging.BotLogger;

@UtilityClass
class PosixFileSystemUtility {

  private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-rw-r--"); // 0664
  private static final boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

  static void setPermissionsIfPosix(Path path) {
    try {
      if (isPosix) {
        Files.setPosixFilePermissions(path, FILE_PERMISSIONS);
      }
    } catch (IOException e) {
      BotLogger.error("Failed to set Posix permissions", e);
    }
  }
}
