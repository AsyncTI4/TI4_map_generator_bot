package ti4.spring.service.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = DiscordOpaqueTokenIntrospector.class)
class DiscordOpaqueTokenIntrospectorTest {

    @Autowired
    private DiscordOpaqueTokenIntrospector introspector;

    @Test
    void throwsOnNullToken() {
        assertThatThrownBy(() -> introspector.introspect(null))
            .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
