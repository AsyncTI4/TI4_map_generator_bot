package ti4.service.megagame;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.TransactionHelper;
import ti4.model.Mapper;
import ti4.service.game.MegagameUndoService;
import ti4.testUtils.BaseTi4Test;

class MegagameTradeOverviewServiceTest extends BaseTi4Test {

    @Test
    void onlyActiveForMegagameTagOutsideFog() {
        Game game = MegagameGroupServiceTest.gameWithCards();
        assertThat(MegagameTradeOverviewService.isActive(game)).isFalse();

        game.addTag(MegagameUndoService.MEGAGAME_TAG);
        assertThat(MegagameTradeOverviewService.isActive(game)).isTrue();

        game.setFowMode(true);
        assertThat(MegagameTradeOverviewService.isActive(game)).isFalse();
    }

    @Test
    void phaseSubstatesShareOneSection() {
        assertThat(MegagameTradeOverviewService.phaseLabel("agendawaiting"))
                .isEqualTo(MegagameTradeOverviewService.phaseLabel("agendaVoting"))
                .isEqualTo("Agenda Phase");
        assertThat(MegagameTradeOverviewService.phaseLabel("statusHomework")).isEqualTo("Status Phase");
        assertThat(MegagameTradeOverviewService.phaseLabel("")).isEqualTo("Unknown Phase");
    }

    @Test
    void fieldValueFitsTheEmbedLimitAndShowsNothingWhenEmpty() {
        assertThat(MegagameTradeOverviewService.fieldValue(List.of())).isEqualTo("- Nothing");
        String capped = MegagameTradeOverviewService.fieldValue(Collections.nCopies(300, "Some long trade item"));
        assertThat(capped).hasSizeLessThanOrEqualTo(MessageEmbed.VALUE_MAX_LENGTH);
    }

    @Test
    void publicContributionReadsBothSidesFromTheOfferersListAndHidesCardNames() {
        Game game = MegagameGroupServiceTest.gameWithCards();
        Player sol = MegagameGroupServiceTest.player(game, "sol");
        Player hacan = MegagameGroupServiceTest.player(game, "hacan");
        hacan.setActionCard("sabo1", 7);
        // the offerer (sol) holds the whole deal, including what hacan sends
        sol.addTransactionItem("sendingsol_receivinghacan_TGs_3");
        sol.addTransactionItem("sendinghacan_receivingsol_ACs_7");
        List<String> items = sol.getTransactionItemsWithPlayer(hacan);

        assertThat(TransactionHelper.describePublicContribution(game, items, sol))
                .hasSize(1);
        List<String> hacanSide = TransactionHelper.describePublicContribution(game, items, hacan);
        assertThat(hacanSide).hasSize(1);
        assertThat(hacanSide.getFirst())
                .doesNotContain(Mapper.getActionCard("sabo1").getName());
    }
}
