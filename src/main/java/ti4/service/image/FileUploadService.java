package ti4.service.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.helpers.DateTimeHelper;
import ti4.image.ImageHelper;
import ti4.message.BotLogger;

@UtilityClass
public class FileUploadService {

    public static FileUpload createFileUpload(BufferedImage imageToUpload, float compressionQuality, String filenamePrefix) {
        if (imageToUpload == null) return null;

        String saveLocalFormat = System.getenv("SAVE_LOCAL_FORMAT");
        if (saveLocalFormat != null) {
            try {
                File file = new File(filenamePrefix + "." + saveLocalFormat);
                ImageIO.write(imageToUpload, saveLocalFormat, file);
            } catch (IOException e) {
                BotLogger.log("Could not create File for " + filenamePrefix + "." + saveLocalFormat, e);
            }
        }

        FileUpload fileUpload = null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage mapWithoutTransparentBackground = ImageHelper.redrawWithoutAlpha(imageToUpload);
            String format = ImageHelper.writeWebpOrDefaultTo(mapWithoutTransparentBackground, out, "jpg", compressionQuality);
            String fileName = filenamePrefix + "_" + DateTimeHelper.getFormattedTimestamp() + "." + format;
            fileUpload = FileUpload.fromData(out.toByteArray(), fileName);
        } catch (IOException e) {
            BotLogger.log("Could not create FileUpload for " + filenamePrefix, e);
        }
        return fileUpload;
    }
}
