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
        return createFileUpload(bufferedImage, filenamePrefix, false);
    }

    public static FileUpload createFileUpload(BufferedImage bufferedImage, String filenamePrefix, boolean saveLocalCopy) {
        byte[] imageBytes = ImageHelper.writeJpg(bufferedImage);
        return createFileUpload(imageBytes, filenamePrefix, saveLocalCopy);
    }

    public static FileUpload createFileUpload(byte[] bytes, String filenamePrefix) {
        return createFileUpload(bytes, filenamePrefix, false);
    }

    public static FileUpload createFileUpload(byte[] bytes, String filenamePrefix, boolean saveLocalCopy) {
        if (bytes == null || bytes.length == 0) return null;

        if (saveLocalCopy)
            optionallySaveToLocal(bytes, filenamePrefix, "jpg");

        String fileName = filenamePrefix + "_" + DateTimeHelper.getFormattedTimestamp() + ".jpg";
        return FileUpload.fromData(bytes, fileName);
    }

    public static void saveLocalPng(BufferedImage image, String filenamePrefix) {
        byte[] imageBytes = ImageHelper.writePng(image);
        optionallySaveToLocal(imageBytes, filenamePrefix, "png");
    }

    private static void optionallySaveToLocal(byte[] bytes, String filenamePrefix, String filenameSuffix) {
        String mapImageStoragePath = Storage.getStoragePath() + File.separator + "mapImages" + File.separator + filenamePrefix + "." + filenameSuffix;
        try (FileOutputStream fileOutputStream = new FileOutputStream(mapImageStoragePath)) {
            fileOutputStream.write(bytes);
        } catch (IOException e) {
            BotLogger.error("Could not create File for " + filenamePrefix + "." + filenameSuffix, e);
        }
    }
}
