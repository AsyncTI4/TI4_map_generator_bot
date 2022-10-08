package ti4.helpers;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import ti4.ResourceHelper;
import ti4.message.BotLogger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Properties;


public class WebHelper {
    private static final Properties webProperties;

    static {
        webProperties = new Properties();
        try (InputStream input = new FileInputStream(Objects.requireNonNull(ResourceHelper.getInstance().getWebFile("web.properties")))) {
            webProperties.load(input);
        } catch (IOException e) {
            BotLogger.log("Could not load web properties.");
        }

    }

    public static void putMap(String gameId, BufferedImage img) {
        try {
            Region region = Region.US_EAST_1;
            S3Client s3 = S3Client.builder()
                    .region(region)
                    .build();

            LocalDateTime date = LocalDateTime.now();
            String dtstamp = date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(webProperties.getProperty("bucket"))
                    .key(String.format("map/%s/%s.png", gameId, dtstamp))
                    .contentType("image/png")
                    .build();

            ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("png").next();
            imageWriter.setOutput(ImageIO.createImageOutputStream(out));
            ImageWriteParam defaultWriteParam = imageWriter.getDefaultWriteParam();
            if (defaultWriteParam.canWriteCompressed()) {
                defaultWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                defaultWriteParam.setCompressionQuality(0.01f);
            }

            try {
                imageWriter.write(null, new IIOImage(img, null, null), defaultWriteParam);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            s3.putObject(request, RequestBody.fromBytes(out.toByteArray()));
        } catch (Exception e) {
            BotLogger.log("Could not add image to web server");
        }
    }
}
