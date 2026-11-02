package ti4.image.io;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;

@UtilityClass
public class ImageUploads {

    public static FileUpload createFileUpload(BufferedImage bufferedImage, String filenamePrefix, String fileFormat) {
        byte[] imageBytes =
                switch (fileFormat) {
                    case "png" -> ImageEncoder.png(bufferedImage);
                    case "jpg" -> ImageEncoder.jpg(bufferedImage);
                    case "webp" -> ImageEncoder.webp(bufferedImage);
                    default -> throw new IllegalArgumentException("Unsupported file format: " + fileFormat);
                };
        return createFileUpload(imageBytes, filenamePrefix, fileFormat);
    }

    public static FileUpload createFileUpload(BufferedImage bufferedImage, String filenamePrefix) {
        return createFileUpload(bufferedImage, filenamePrefix, "webp");
    }

    public static FileUpload createFileUpload(byte[] bytes, String filenamePrefix, String fileFormat) {
        if (bytes == null || bytes.length == 0) return null;

        String fileName = filenamePrefix + "_" + DateTimeHelper.getFormattedTimestamp() + "." + fileFormat;
        return FileUpload.fromData(bytes, fileName);
    }

    public static void saveLocalPng(BufferedImage image, String filenamePrefix) {
        byte[] imageBytes = ImageEncoder.png(image);
        optionallySaveToLocal(imageBytes, filenamePrefix, "png");
    }

    private static void optionallySaveToLocal(byte[] bytes, String filenamePrefix, String filenameSuffix) {
        String mapImageStoragePath = Storage.getStoragePath() + File.separator + "mapImages" + File.separator
                + filenamePrefix + "." + filenameSuffix;
        try (FileOutputStream fileOutputStream = new FileOutputStream(mapImageStoragePath)) {
            fileOutputStream.write(bytes);
        } catch (IOException e) {
            BotLogger.error("Could not create File for " + filenamePrefix + "." + filenameSuffix, e);
        }
    }
}
