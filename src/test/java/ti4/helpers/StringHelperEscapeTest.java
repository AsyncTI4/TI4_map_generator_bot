package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringHelperEscapeTest {

    @Test
    void unescapeReversesEscape() {
        String original = "a-b_c:d;e|f,g\nh i";
        assertThat(StringHelper.unescape(StringHelper.escape(original))).isEqualTo(original);
    }

    @Test
    void unescapeLeavesPlainTextAlone() {
        // Plain stored values skip the replacement passes, so they must come back as the same instance.
        String plain = "plainvalue123";
        assertThat(StringHelper.unescape(plain)).isSameAs(plain);
        assertThat(StringHelper.unescape("")).isEmpty();
    }

    @Test
    void unescapeStillHandlesLegacyMarkers() {
        assertThat(StringHelper.unescape("a666finb667finc")).isEqualTo("a:b,c");
        assertThat(StringHelper.unescape("line\r")).isEqualTo("line");
        assertThat(StringHelper.unescape("x{sp}y")).isEqualTo("x y");
    }
}
