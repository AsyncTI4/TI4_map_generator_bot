package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.service.transaction.TransactionItem;

class TransactionHelperTest {

    @Test
    void publicEventItemsHidePrivateCardIds() {
        List<TransactionItem> items = List.of(
                        "sendingkeleresm_receivingsardakk_PNs_15",
                        "sendingsardakk_receivingkeleresm_ACs_42",
                        "sendingsardakk_receivingkeleresm_SOs_secret_id",
                        "sendingkeleresm_receivingsardakk_PNs_generic2",
                        "sendingkeleresm_receivingsardakk_details_Thefin777dealfin777isfin777thefin777usual",
                        "sendingkeleresm_receivingsardakk_TGs_3")
                .stream()
                .map(TransactionItem::parse)
                .toList();

        List<String> sanitized = TransactionHelper.hidePrivateCardIdsForPublicEvent(items);

        assertThat(sanitized)
                .containsExactly(
                        "sendingkeleresm_receivingsardakk_PNs_generic1",
                        "sendingsardakk_receivingkeleresm_ACs_generic1",
                        "sendingsardakk_receivingkeleresm_SOs_generic1",
                        "sendingkeleresm_receivingsardakk_PNs_generic2",
                        "sendingkeleresm_receivingsardakk_details_Thefin777dealfin777isfin777thefin777usual",
                        "sendingkeleresm_receivingsardakk_TGs_3");
    }
}
