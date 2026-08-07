package ti4.service.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransactionItemTest {

    @Test
    void serializesToLegacyFormat() {
        assertThat(TransactionItem.of("sardakk", "keleresm", "PNs", "15").serialize())
                .isEqualTo("sendingsardakk_receivingkeleresm_PNs_15");
    }

    @Test
    void roundTripsLegacyFormatWithUnderscoresInDetail() {
        String item = "sendingsardakk_receivingkeleresm_SOs_secret_id";

        assertThat(TransactionItem.parse(item).serialize()).isEqualTo(item);
    }

    @Test
    void hidesSpecificPrivateCardIds() {
        assertThat(TransactionItem.parse("sendingsardakk_receivingkeleresm_ACs_42")
                        .hidePrivateCardIdForPublicEvent()
                        .serialize())
                .isEqualTo("sendingsardakk_receivingkeleresm_ACs_generic1");
    }

    @Test
    void leavesGenericPrivateCardsAlone() {
        assertThat(TransactionItem.parse("sendingsardakk_receivingkeleresm_PNs_generic2")
                        .hidePrivateCardIdForPublicEvent()
                        .serialize())
                .isEqualTo("sendingsardakk_receivingkeleresm_PNs_generic2");
    }
}
