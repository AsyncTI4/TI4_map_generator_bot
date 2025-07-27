package ti4.spring.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PingControllerTest {
    @Test
    void pingReturnsPong() {
        PingController controller = new PingController();
        ResponseEntity<String> response = controller.ping();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("pong");
    }
}
