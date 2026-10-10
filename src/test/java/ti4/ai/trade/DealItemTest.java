package ti4.ai.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DealItemTest {

    private static DealItem parsed(String raw) {
        return DealItem.parse(raw, "nekro", "sol").orElseThrow();
    }

    // Every item the engine can store between the two factions parses, prints back to the exact engine string and
    // survives a round trip through the memory encoding, including the ones the AI cannot judge.
    @Test
    void everyTypeRoundTrips() {
        List<String> raws = List.of(
                "sendingnekro_receivingsol_TGs_5",
                "sendingsol_receivingnekro_Comms_3",
                "sendingnekro_receivingsol_SendDebt_2",
                "sendingsol_receivingnekro_ClearDebt_1",
                "sendingnekro_receivingsol_PNs_42",
                "sendingsol_receivingnekro_PNs_bluefin9cf",
                "sendingnekro_receivingsol_PNs_antivirus",
                "sendingnekro_receivingsol_PNs_generic1",
                "sendingnekro_receivingsol_PNs_genericx",
                "sendingsol_receivingnekro_Frags_CRF2",
                "sendingsol_receivingnekro_Frags_supermassive_fragment",
                "sendingsol_receivingnekro_ACs_17",
                "sendingsol_receivingnekro_details_payfin777mefin777later;~:>",
                "sendingsol_receivingnekro_action");

        for (String raw : raws) {
            DealItem item = parsed(raw);
            assertThat(item.engineString()).isEqualTo(raw);
            assertThat(DealItem.decode(item.encode())).contains(item);
            assertThat(item.encode()).doesNotContain(";", "~");
        }
    }

    @Test
    void readsTypesAndAmounts() {
        assertThat(parsed("sendingnekro_receivingsol_TGs_5"))
                .isEqualTo(new DealItem("nekro", "sol", ItemType.TRADE_GOODS, "5"));
        assertThat(parsed("sendingsol_receivingnekro_TGs_5").sender()).isEqualTo("sol");
        assertThat(parsed("sendingnekro_receivingsol_TGs_5").amount()).isEqualTo(5);
        assertThat(parsed("sendingnekro_receivingsol_PNs_42").amount()).isEqualTo(1);
        assertThat(parsed("sendingsol_receivingnekro_Frags_CRF2").amount()).isEqualTo(2);
        assertThat(parsed("sendingsol_receivingnekro_Frags_CRF2").fragmentKind())
                .isEqualTo("CRF");
        assertThat(parsed("sendingsol_receivingnekro_ACs_17").amount()).isEqualTo(1);
    }

    // A note the sender does not hold is named by alias with "_" written as "fin9"; "generic1" is the "TBD
    // Promissory Note" the receiver picks after the deal.
    @Test
    void recognisesAliasedAndGenericNotes() {
        DealItem aliased = parsed("sendingsol_receivingnekro_PNs_bluefin9cf");
        assertThat(aliased.type()).isEqualTo(ItemType.PROMISSORY);
        assertThat(aliased.isGenericNote()).isFalse();

        DealItem generic = parsed("sendingnekro_receivingsol_PNs_generic1");
        assertThat(generic.type()).isEqualTo(ItemType.PROMISSORY);
        assertThat(generic.isGenericNote()).isTrue();
        assertThat(generic.amount()).isEqualTo(1);
    }

    // A faction note's alias has no "_", so when the sender does not hold it the engine's request button carries the
    // bare alias (TransactionHelper: pnShortHand.replace("_", "fin9")).
    @Test
    void recognisesBareAliasNotes() {
        DealItem bare = parsed("sendingnekro_receivingsol_PNs_antivirus");

        assertThat(bare.type()).isEqualTo(ItemType.PROMISSORY);
        assertThat(bare.isGenericNote()).isFalse();
        assertThat(bare.amount()).isEqualTo(1);
    }

    // Note details the engine never builds, and that it or the AI would choke on: a "generic" without its count, a
    // hand id too long to be one, and an alias with a raw "_".
    @Test
    void marksMalformedNotesAsUnsupported() {
        assertThat(parsed("sendingnekro_receivingsol_PNs_genericx").type()).isEqualTo(ItemType.UNSUPPORTED);
        assertThat(parsed("sendingnekro_receivingsol_PNs_12345678901").type()).isEqualTo(ItemType.UNSUPPORTED);
        assertThat(parsed("sendingnekro_receivingsol_PNs_blue_cf").type()).isEqualTo(ItemType.UNSUPPORTED);
    }

    // A supermassive fragment, cards, free-text details and malformed amounts are kept, but as items the AI cannot
    // judge.
    @Test
    void marksWhatItCannotJudgeAsUnsupported() {
        assertThat(parsed("sendingsol_receivingnekro_Frags_supermassive_fragment")
                        .type())
                .isEqualTo(ItemType.UNSUPPORTED);
        assertThat(parsed("sendingsol_receivingnekro_ACs_17").type()).isEqualTo(ItemType.UNSUPPORTED);
        assertThat(parsed("sendingsol_receivingnekro_details_text").type()).isEqualTo(ItemType.UNSUPPORTED);
        assertThat(parsed("sendingsol_receivingnekro_TGs_lots").type()).isEqualTo(ItemType.UNSUPPORTED);
    }

    // Only exact "sending<a>_receiving<b>_" prefixes count, so a faction that starts with another faction's id is not
    // mistaken for it, and items with a third player are ignored.
    @Test
    void matchesExactFactionsOnly() {
        assertThat(DealItem.parse("sendingsolx_receivingnekro_TGs_1", "nekro", "sol"))
                .isEmpty();
        assertThat(DealItem.parse("sendingnekro_receivingletnev_TGs_1", "nekro", "sol"))
                .isEmpty();
        assertThat(DealItem.parse("garbage", "nekro", "sol")).isEmpty();
    }

    // The engine splits items on "_", so a faction whose id contains one cannot be traded with safely.
    @Test
    void refusesFactionsWithUnderscores() {
        assertThat(DealItem.parse("sendingpi_x_receivingsol_TGs_1", "pi_x", "sol"))
                .isEmpty();
        assertThat(DealItem.tradable("pi_x")).isFalse();
        assertThat(DealItem.tradable("sol")).isTrue();
    }

    @Test
    void knowsWhichTypesItBuilds() {
        assertThat(ItemType.of("TGs")).isEqualTo(ItemType.TRADE_GOODS);
        assertThat(ItemType.of("ACs")).isEqualTo(ItemType.UNSUPPORTED);
        assertThat(ItemType.of("")).isEqualTo(ItemType.UNSUPPORTED);
        assertThat(List.of(ItemType.values()).stream().filter(ItemType::buildable))
                .containsExactly(
                        ItemType.TRADE_GOODS,
                        ItemType.COMMODITIES,
                        ItemType.SEND_DEBT,
                        ItemType.CLEAR_DEBT,
                        ItemType.PROMISSORY);
    }

    @Test
    void rejectsCorruptEncodings() {
        assertThat(DealItem.decode("nekro>sol:TGs")).isEmpty();
        assertThat(DealItem.decode("nekro>sol:Bananas:5")).isEmpty();
        assertThat(DealItem.decode("nekro>sol:TGs:five")).isEmpty();
        assertThat(DealItem.decode(null)).isEmpty();
    }
}
