package ti4.website;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.Objects;
import java.util.Properties;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import ti4.ResourceHelper;
import ti4.message.BotLogger;

public class EgressClientManager {

    @Getter
    private static final ObjectMapper objectMapper = new ObjectMapper();
    @Getter
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    @Getter
    private static final S3AsyncClient s3AsyncClient = S3AsyncClient.builder().region(Region.US_EAST_1).build();
    @Getter
    private static final Properties webProperties;

    static {
        webProperties = new Properties();
        try (InputStream input = new FileInputStream(Objects.requireNonNull(ResourceHelper.getInstance().getWebFile("web.properties")))) {
            webProperties.load(input);
        } catch (IOException e) {
            BotLogger.error("Could not load web properties.", e);
        }
    }
}
