package ti4.service.image;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Storage;
import ti4.image.ImageHelper;
import ti4.message.BotLogger;

@UtilityClass
public class FileUploadService {

    public static FileUpload createFileUpload(BufferedImage bufferedImage, String filenamePrefix) {
        byte[] imageBytes = ImageHelper.writeJpg(bufferedImage);
        return createFileUpload(imageBytes, filenamePrefix);
    }

    public static FileUpload createFileUpload(byte[] bytes, String filenamePrefix) {
        if (bytes == null || bytes.length == 0) return null;

        optionallySaveToLocal(bytes, filenamePrefix);

        String fileName = filenamePrefix + "_" + DateTimeHelper.getFormattedTimestamp() + ".jpg";
        return FileUpload.fromData(bytes, fileName);
    }

    private static void optionallySaveToLocal(byte[] bytes, String filenamePrefix) {
        String saveLocal = System.getenv("SAVE_LOCAL");
        if (Boolean.parseBoolean(saveLocal)) {
            String mapImageStoragePath =
                    Storage.getStoragePath() + File.separator + "mapImages" + File.separator + filenamePrefix + ".jpg";
            try (FileOutputStream fileOutputStream = new FileOutputStream(mapImageStoragePath)) {
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                BotLogger.log("Could not create File for " + filenamePrefix + "." + saveLocal, e);
            }
        }
    }
}
