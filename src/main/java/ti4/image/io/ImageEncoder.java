package ti4.image.io;

import com.luciad.imageio.webp.CompressionType;
import com.luciad.imageio.webp.WebPWriteParam;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import lombok.SneakyThrows;

public final class ImageEncoder {

    private static final int WEBP_MAX_DIMENSION = 16_383;
    private static final int WEBP_ENCODER_METHOD = 2;

    private ImageEncoder() {}

    public static EncodedImage forDiscord(BufferedImage canvas) {
        if (canvas.getWidth() > WEBP_MAX_DIMENSION || canvas.getHeight() > WEBP_MAX_DIMENSION) {
            return new EncodedImage(jpg(canvas), "jpg");
        }
        return new EncodedImage(webp(canvas), "webp");
    }

    public static byte[] jpg(BufferedImage image) {
        return write(withoutAlpha(image), "jpg");
    }

    public static byte[] png(BufferedImage image) {
        return write(image, "png");
    }

    @SneakyThrows
    public static byte[] webp(BufferedImage image) {
        BufferedImage opaque = withoutAlpha(image);
        ImageWriter writer = null;
        try (var bytes = new ByteArrayOutputStream();
                var output = ImageIO.createImageOutputStream(bytes)) {
            writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
            writer.setOutput(output);
            writer.write(null, new IIOImage(opaque, null, null), webpParameters(writer));
            output.flush();
            return bytes.toByteArray();
        } finally {
            if (writer != null) writer.dispose();
        }
    }

    private static WebPWriteParam webpParameters(ImageWriter writer) {
        WebPWriteParam parameters = (WebPWriteParam) writer.getDefaultWriteParam();
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionType(CompressionType.Lossy);
        parameters.setUseSharpYUV(false);
        parameters.setAlphaCompressionAlgorithm(0);
        parameters.setMethod(WEBP_ENCODER_METHOD);
        return parameters;
    }

    @SneakyThrows
    private static byte[] write(BufferedImage image, String format) {
        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, bytes);
            return bytes.toByteArray();
        }
    }

    private static BufferedImage withoutAlpha(BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) return image;
        BufferedImage opaque = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = opaque.createGraphics();
        g2.drawImage(image, 0, 0, Color.BLACK, null);
        g2.dispose();
        return opaque;
    }
}
