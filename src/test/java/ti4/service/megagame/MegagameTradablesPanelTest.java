package ti4.service.megagame;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class MegagameTradablesPanelTest extends BaseTi4Test {

    @Test
    void pagesStayWithinTheEmbedCountAndCharacterLimits() {
        MessageEmbed small = new EmbedBuilder().setDescription("x").build();
        assertThat(MegagameTradablesPanel.paginate(Collections.nCopies(25, small)))
                .extracting(List::size)
                .containsExactly(10, 10, 5);

        MessageEmbed big = new EmbedBuilder().setDescription("x".repeat(2000)).build();
        assertThat(MegagameTradablesPanel.paginate(Collections.nCopies(5, big)))
                .allSatisfy(page -> assertThat(
                                page.stream().mapToInt(MessageEmbed::getLength).sum())
                        .isLessThanOrEqualTo(MessageEmbed.EMBED_MAX_LENGTH_BOT));
    }

    @Test
    void debtIsSplitPerFactionFromBothSides() {
        Game game = MegagameGroupServiceTest.gameWithCards();
        game.setName("tradablesPanelTest");
        Player sol = MegagameGroupServiceTest.player(game, "sol");
        Player hacan = MegagameGroupServiceTest.player(game, "hacan");
        Player jolnar = MegagameGroupServiceTest.player(game, "jolnar");
        // hacan holds 2 of sol's tokens in the default pool and 1 of jolnar's in a custom bank
        hacan.addDebtTokens(sol.getColor(), 2);
        hacan.addDebtTokens(jolnar.getColor(), 1, "bank");

        String hacanPanel = describe(game, hacan);
        assertThat(hacanPanel).contains("Holds: ").contains("×2").contains("Bank: ");
        assertThat(hacanPanel).doesNotContain("Owes:");

        String solPanel = describe(game, sol);
        assertThat(solPanel).contains("Owes: " + hacan.getFactionEmoji() + "×2");
        assertThat(solPanel).doesNotContain("Holds:");
    }

    @Test
    void notesOutListOnlyPlayAreaNotesWithTheirHolder() {
        Game game = MegagameGroupServiceTest.gameWithCards();
        game.setName("tradablesPanelNotesTest");
        Player sol = MegagameGroupServiceTest.player(game, "sol");
        Player hacan = MegagameGroupServiceTest.player(game, "hacan");
        String throne = hacan.getColor() + "_sftt";
        String tradeAgreement = hacan.getColor() + "_ta";
        hacan.addOwnedPromissoryNoteByID(throne);
        hacan.addOwnedPromissoryNoteByID(tradeAgreement);
        // Support for the Throne sits in sol's play area (public); the Trade Agreement stays hidden in sol's hand
        sol.addPromissoryNoteToPlayArea(throne);
        sol.setPromissoryNote(tradeAgreement);

        String hacanPanel = describe(game, hacan);
        assertThat(hacanPanel).contains("Notes out: Support for the Throne → " + sol.getFactionEmoji());
        assertThat(hacanPanel).doesNotContain("Trade Agreement");
        assertThat(describe(game, sol)).doesNotContain("Notes out:");
    }

    private static String describe(Game game, Player player) {
        return MegagameTradablesPanel.pages(game).stream()
                .flatMap(List::stream)
                .map(MessageEmbed::getDescription)
                .filter(description ->
                        description.contains("**" + MegagameTradeOverviewService.shortName(player) + "**"))
                .findFirst()
                .orElseThrow();
    }
}
