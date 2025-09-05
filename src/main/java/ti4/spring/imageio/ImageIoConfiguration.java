package ti4.spring.imageio;

import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageWriterSpi;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ti4.message.logging.BotLogger;

@Configuration
public class ImageIoConfiguration {

    @Bean
    ApplicationRunner init() {
        return args -> {
            // For some reason the webp plugin doesn't get registered sometimes, which is fixed by restarting the bot
            boolean webpRegistered = ensureWebpWriterRegistered();
            if (!webpRegistered) {
                BotLogger.error("No WebP writer detected at startup.");
                throw new ApplicationContextException("WebP plugin not found on classpath.");
            }
            // this seems recommended everywhere I look
            ImageIO.setUseCache(false);
        };
    }

    private static boolean ensureWebpWriterRegistered() {
        ClassLoader appCl = ImageIoConfiguration.class.getClassLoader();
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(appCl);

            ImageIO.scanForPlugins();
            if (hasWebpWriter()) return true;

            Class<?> spiKlass = Class.forName("com.luciad.imageio.webp.WebPImageWriterSpi", false, appCl);
            ImageWriterSpi spi =
                    (ImageWriterSpi) spiKlass.getDeclaredConstructor().newInstance();
            IIORegistry.getDefaultInstance().registerServiceProvider(spi);

            return hasWebpWriter();
        } catch (Exception e) {
            BotLogger.error("Failed to register WebP SPI explicitly", e);
            return hasWebpWriter();
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    private static boolean hasWebpWriter() {
        boolean hasWebpWriterQuickCheck =
                Arrays.stream(ImageIO.getWriterFormatNames()).anyMatch("webp"::equalsIgnoreCase);
        if (hasWebpWriterQuickCheck) return true;

        Iterator<ImageWriterSpi> it = IIORegistry.getDefaultInstance().getServiceProviders(ImageWriterSpi.class, true);
        while (it.hasNext()) {
            ImageWriterSpi spi = it.next();
            for (String n : spi.getFormatNames()) {
                if ("webp".equalsIgnoreCase(n)) return true;
            }
        }
        return false;
    }
}
