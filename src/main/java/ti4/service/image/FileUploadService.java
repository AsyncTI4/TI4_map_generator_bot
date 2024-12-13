package ti4.service.image;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.DateTimeHelper;
import ti4.image.ImageHelper;
import ti4.message.BotLogger;

@UtilityClass
public class FileUploadService {

    public static FileUpload createWebpFileUpload(BufferedImage bufferedImage, String fileNamePrefix) {
        byte[] bytes = ImageHelper.writeImage(bufferedImage);
        return createFileUpload(bytes, fileNamePrefix, "webp");
    }

    public static FileUpload createWebpFileUpload(byte[] bytes, String filenamePrefix) {
        return createFileUpload(bytes, filenamePrefix, "webp");
    }

    public static FileUpload createFileUpload(byte[] bytes, String filenamePrefix, String format) {
        if (bytes == null || bytes.length == 0) return null;

        optionallySaveToLocal(bytes, filenamePrefix, format);

        String fileName = filenamePrefix + "_" + DateTimeHelper.getFormattedTimestamp() + "." + format;
        return FileUpload.fromData(bytes, fileName);
    }

    private static void optionallySaveToLocal(byte[] bytes, String filenamePrefix, String format) {
        String saveLocal = System.getenv("SAVE_LOCAL");
        if (Boolean.parseBoolean(saveLocal)) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(filenamePrefix + "." + format)) {
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                BotLogger.log("Could not create File for " + filenamePrefix + "." + format, e);
            }
        }
    }
}
