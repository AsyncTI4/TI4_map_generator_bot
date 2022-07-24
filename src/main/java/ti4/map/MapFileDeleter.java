package ti4.map;

import ti4.helpers.Constants;
import ti4.helpers.Storage;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class MapFileDeleter {
    public static Set<File> filesToDelete = new HashSet<>();

    public static void deleteFiles() {
        for (File file : filesToDelete) {
            file.delete();
        }

        File mapImageDirectory = Storage.getMapImageDirectory();

        System.out.println("FILE0: " + mapImageDirectory.exists());
        System.out.println("FILE1: " + Storage.getStoragePath());
        System.out.println("FILE2: " + new File(Storage.getStoragePath()).getAbsolutePath());
        System.out.println("----------------------");
        System.out.println("VAR2: " + new File("./ti4bot/ti4bot_saves" + Storage.MAPS).exists());
        System.out.println("VAR3: " + new File("./ti4bot/ti4bot_saves", Storage.MAPS).exists());
        System.out.println("VAR3: " + new File("./ti4bot/ti4bot_saves", Storage.MAPS).getAbsolutePath());
        System.out.println("VAR3: " + new File("./ti4bot/ti4bot_saves", Storage.MAPS).toString());
        System.out.println("VAR3: " + new File("./ti4bot/ti4bot_saves", Storage.MAPS).toPath());


        for (File file : mapImageDirectory.listFiles()) {
            String absolutePath = file.getAbsolutePath();
            if (absolutePath.endsWith(Constants.JPG) ||
                    absolutePath.endsWith(Constants.PNG)) {
                file.delete();
            }
        }
    }

    public static void addFileToDelete(File fileToDelete) {
        filesToDelete.add(fileToDelete);
    }
}
