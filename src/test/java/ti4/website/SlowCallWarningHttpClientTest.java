package ti4.website;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;

class SlowCallWarningHttpClientTest {

    @Test
    void describeDropsTheQueryStringSoTokensNeverReachTheLogs() {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com/api/thing?token=secret"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        assertThat(SlowCallWarningHttpClient.describe(request)).isEqualTo("POST https://example.com/api/thing");
    }
}
