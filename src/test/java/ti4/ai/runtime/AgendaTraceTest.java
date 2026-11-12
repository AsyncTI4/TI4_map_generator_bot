package ti4.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgendaTraceTest {

    @Test
    void offerItemsReadAsWhoGivesWhat() {
        String items = AgendaTrace.items(List.of(
                "sendingxxcha_receivingnekro_TGs_3",
                "sendingxxcha_receivingnekro_ACs_generic2",
                "sendingnekro_receivingxxcha_PNs_ps",
                "sendingnekro_receivingxxcha_details_vote fin777for"));
        assertEquals("{xxcha gives nekro 3 TG, 2 AC; nekro gives xxcha PN ps, note \"vote  for\"}", items);
    }

    @Test
    void anEmptyOfferSaysSo() {
        assertEquals("{nothing}", AgendaTrace.items(List.of()));
    }
}
