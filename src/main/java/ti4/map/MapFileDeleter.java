package ti4.map;

import ti4.helpers.Constants;
import ti4.helpers.Storage;

import java.io.File;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class MapFileDeleter {
    public static Set<File> filesToDelete = new HashSet<>();

    public static void deleteFiles() {
        for (File file : filesToDelete) {
            file.delete();
        }
        File mapImageDirectory = Storage.getMapImageDirectory();
        if(Optional.ofNullable(mapImageDirectory.listFiles()).isPresent()) {
            for (File file : mapImageDirectory.listFiles()) {
                String absolutePath = file.getAbsolutePath();
                if (absolutePath.endsWith(Constants.JPG) ||
                        absolutePath.endsWith(Constants.PNG)) {
                    file.delete();
                }
            }
        }
    }

    public static void addFileToDelete(File fileToDelete) {
        filesToDelete.add(fileToDelete);
    }
}
