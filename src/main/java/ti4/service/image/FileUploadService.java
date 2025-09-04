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
import ti4.message.logging.BotLogger;

@UtilityClass
public class FileUploadService {

    public static FileUpload createFileUpload(BufferedImage bufferedImage, String filenamePrefix, String fileFormat) {
        return createFileUpload(bufferedImage, filenamePrefix, fileFormat, false);
    }

    public static FileUpload createFileUpload(BufferedImage bufferedImage, String filenamePrefix) {
        return createFileUpload(bufferedImage, filenamePrefix, "webp", false);
    }

    private static FileUpload createFileUpload(
        BufferedImage bufferedImage, String filenamePrefix, String fileFormat, boolean saveLocalCopy
    ) {
        byte[] imageBytes;
        switch (fileFormat) {
            case "png" -> imageBytes = ImageHelper.writePng(bufferedImage);
            case "jpg" -> imageBytes = ImageHelper.writeJpg(bufferedImage);
            case "webp" -> imageBytes = ImageHelper.writeWebp(bufferedImage);
            default -> throw new IllegalArgumentException("Unsupported file format: " + fileFormat);
        }
        return createFileUpload(imageBytes, filenamePrefix, fileFormat, saveLocalCopy);
    }

    public static FileUpload createFileUpload(byte[] bytes, String filenamePrefix, String fileFormat) {
        return createFileUpload(bytes, filenamePrefix, fileFormat, false);
    }

    private static FileUpload createFileUpload(
        byte[] bytes, String filenamePrefix, String fileFormat, boolean saveLocalCopy
    ) {
        if (bytes == null || bytes.length == 0) return null;

        if (saveLocalCopy) optionallySaveToLocal(bytes, filenamePrefix, fileFormat);

        String fileName = filenamePrefix + "_" + DateTimeHelper.getFormattedTimestamp() + "." + fileFormat;
        return FileUpload.fromData(bytes, fileName);
    }

    public static void saveLocalPng(BufferedImage image, String filenamePrefix) {
        byte[] imageBytes = ImageHelper.writePng(image);
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
