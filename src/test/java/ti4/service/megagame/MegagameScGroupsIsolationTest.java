package ti4.service.megagame;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.Helper;
import ti4.helpers.thundersedge.TeHelperActionCards;
import ti4.testUtils.BaseTi4Test;

class MegagameScGroupsIsolationTest extends BaseTi4Test {

    @Test
    void pickButtonsShowOnlyTheOwnGroupsCards() {
        Game game = MegagameGroupServiceTest.threeGroups();
        Player jolnar = MegagameGroupServiceTest.player(game, "jolnar");

        assertThat(pickIds(game, jolnar)).allMatch(id -> id.endsWith("2")).hasSize(8);
    }

    @Test
    void untaggedFogAndUnconfiguredGamesKeepEveryCard() {
        Game untagged = MegagameGroupServiceTest.threeGroups();
        untagged.getTags().clear();
        Game fog = MegagameGroupServiceTest.threeGroups();
        fog.setFowMode(true);
        Game unconfigured = MegagameGroupServiceTest.gameWithCards();
        unconfigured.addTag(MegagameGroupService.SC_GROUPS_TAG);

        for (Game game : List.of(untagged, fog, unconfigured)) {
            assertThat(pickIds(game, MegagameGroupServiceTest.player(game, "jolnar")))
                    .hasSize(40);
            assertThat(MegagameGroupService.allowsCard(game, MegagameGroupServiceTest.player(game, "sol"), 12))
                    .isTrue();
        }
    }

    @Test
    void nextPickerStartsAtTheGroupSpeakerSkipsHoldersAndStopsWhenCardsRunOut() {
        Game game = MegagameGroupServiceTest.threeGroups();
        Player sol = MegagameGroupServiceTest.player(game, "sol");
        Player hacan = MegagameGroupServiceTest.player(game, "hacan");
        MegagameGroupService.setGroupSpeaker(game, "A", hacan);
        game.setStrategyCardsPerPlayer(4);

        assertThat(MegagameStrategyPhaseService.nextPicker(game, "A")).isEqualTo(hacan);
        hacan.addSC(11);
        assertThat(MegagameStrategyPhaseService.nextPicker(game, "A")).isEqualTo(sol);
        for (int card = 2; card <= 8; card++) {
            (card % 2 == 0 ? sol : hacan).addSC(card * 10 + 1);
        }
        assertThat(MegagameStrategyPhaseService.nextPicker(game, "A")).isNull();
    }

    @Test
    void strategizeOffersSecondariesOfTheOwnGroupsReadiedCards() {
        Game game = MegagameGroupServiceTest.threeGroups();
        game.setSCPlayed(12, true);
        Player jolnar = MegagameGroupServiceTest.player(game, "jolnar");
        Player sol = MegagameGroupServiceTest.player(game, "sol");

        assertThat(strategizeIds(game, jolnar)).doesNotContain("leadershipGenerateCCButtons");
        assertThat(strategizeIds(game, sol)).contains("leadershipGenerateCCButtons");

        game.getTags().clear();
        assertThat(strategizeIds(game, jolnar)).contains("leadershipGenerateCCButtons");
    }

    @Test
    void outsidersAreEveryoneNotInTheCardsGroup() {
        Game game = MegagameGroupServiceTest.threeGroups();

        assertThat(MegagameGroupService.outsidersOfScGroup(game, 21))
                .extracting(Player::getFaction)
                .containsExactlyInAnyOrder("jolnar", "xxcha", "letnev", "naalu");
        assertThat(MegagameGroupService.followOrderForSc(game, 21, MegagameGroupServiceTest.player(game, "hacan")))
                .extracting(Player::getFaction)
                .containsExactly("hacan", "sol");
    }

    @Test
    void lastToPassCountsOnlyTheOwnGroup() {
        Game game = MegagameGroupServiceTest.threeGroups();
        Player sol = MegagameGroupServiceTest.player(game, "sol");
        Player hacan = MegagameGroupServiceTest.player(game, "hacan");

        sol.setPassed(true);
        assertThat(MegagameGroupService.isLastToPassInGroup(game, sol)).isFalse();
        hacan.setPassed(true);
        assertThat(MegagameGroupService.isLastToPassInGroup(game, hacan)).isTrue();

        game.getTags().clear();
        assertThat(MegagameGroupService.isLastToPassInGroup(game, hacan)).isFalse();
    }

    @Test
    void quantumDatahubNodeTargetsOnlyTheOwnGroupButImperialArbiterStillSeesEveryone() {
        Game game = MegagameGroupServiceTest.threeGroups();
        int[] cards = {11, 21, 12, 22, 13, 23};
        for (int i = 0; i < cards.length; i++) {
            MegagameGroupServiceTest.player(game, MegagameGroupServiceTest.FACTIONS.get(i))
                    .addSC(cards[i]);
        }
        Player hacan = MegagameGroupServiceTest.player(game, "hacan");

        assertThat(ButtonHelperFactionSpecific.getSwapSCButtons(game, "qdn", hacan))
                .hasSize(1);
        assertThat(ButtonHelperFactionSpecific.getSwapSCButtons(game, "imperialarbiter", hacan))
                .hasSize(5);
    }

    @Test
    void naazHeroOffersSecondariesOfTheOwnGroupsUnplayedCards() {
        Game game = MegagameGroupServiceTest.threeGroups();
        game.setSCPlayed(12, true);

        assertThat(naazHeroIds(game, "jolnar")).doesNotContain("leadershipGenerateCCButtons");
        assertThat(naazHeroIds(game, "sol")).contains("leadershipGenerateCCButtons");
    }

    @Test
    void eliminatingAGroupSpeakerWarnsTheGmInsteadOfFixingAnything() {
        Game game = MegagameGroupServiceTest.threeGroups();
        Player sol = MegagameGroupServiceTest.player(game, "sol");
        assertThat(MegagameWarnings.of(game)).isEmpty();

        sol.setEliminated(true);
        sol.setDummy(true);

        assertThat(MegagameWarnings.of(game))
                .anyMatch(warning -> warning.contains("still lists sol"))
                .anyMatch(warning -> warning.contains("has no speaker"));
        assertThat(MegagameGroupService.memberFactions(game, "A")).contains("sol");
    }

    @Test
    void threadNamesCarryCardAndGroupAndParseBack() {
        String name = MegagameScThreads.threadName("pbd30k-round-2-Leadership", 12);

        assertThat(name).isEqualTo("pbd30k-round-2-Leadership-12-Beta");
        assertThat(MegagameScThreads.scOfThreadName("Leadership-12-Beta")).isEqualTo(12);
    }

    private static List<String> naazHeroIds(Game game, String faction) {
        return ButtonHelperHeroes.getNRAHeroButtons(game, MegagameGroupServiceTest.player(game, faction)).stream()
                .map(Button::getCustomId)
                .toList();
    }

    private static List<String> pickIds(Game game, Player player) {
        return Helper.getRemainingSCButtons(game, player).stream()
                .map(Button::getCustomId)
                .toList();
    }

    private static List<String> strategizeIds(Game game, Player player) {
        return TeHelperActionCards.getReadiedStrategyCardSecondaryButtons(game, player).stream()
                .map(Button::getCustomId)
                .toList();
    }
}
