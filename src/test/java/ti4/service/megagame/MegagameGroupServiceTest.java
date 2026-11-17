package ti4.service.megagame;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class MegagameGroupServiceTest extends BaseTi4Test {

    static final List<String> FACTIONS = List.of("sol", "hacan", "jolnar", "xxcha", "letnev", "naalu");
    private static final List<String> COLOURS = List.of("red", "blue", "green", "yellow", "purple", "black");

    @Test
    void scGroupComesFromTheLastInitiativeDigit() {
        assertThat(MegagameGroupService.groupOfSc(11)).isEqualTo("A");
        assertThat(MegagameGroupService.groupOfSc(85)).isEqualTo("E");
        assertThat(MegagameGroupService.groupOfSc(30)).isNull();
        assertThat(MegagameGroupService.groupOfSc(3)).isNull();
    }

    @Test
    void initFromHeldCardsKeepsSeatOrderAndLastGroupUsesGameSpeaker() {
        Game game = gameWithCards(11, 21, 12, 22, 13, 23);
        game.setSpeaker(player(game, "letnev"));

        MegagameGroupService.initGroups(game);

        assertThat(MegagameGroupService.groups(game)).containsExactly("A", "B", "C");
        assertThat(MegagameGroupService.memberFactions(game, "B")).containsExactly("jolnar", "xxcha");
        assertThat(MegagameGroupService.groupSpeaker(game, "A").getFaction()).isEqualTo("sol");
        assertThat(MegagameGroupService.groupSpeaker(game, "C").getFaction()).isEqualTo("letnev");
        assertThat(game.getStrategyCardsPerPlayer()).isEqualTo(4);
    }

    @Test
    void initWithoutCardsChunksSeatOrderOverTheCardGroups() {
        Game game = gameWithCards();

        MegagameGroupService.initGroups(game);

        // 8 cards per group leave 2 unpicked, so 6 seats fit in one group
        assertThat(MegagameGroupService.groups(game)).containsExactly("A");
        assertThat(MegagameGroupService.memberFactions(game, "A")).hasSize(6);
        assertThat(game.getStrategyCardsPerPlayer()).isEqualTo(1);
    }

    @Test
    void rotateShiftsMembersAndSpeakersAndTheSecondToLastSpeakerBecomesGameSpeaker() {
        Game game = threeGroups();
        MegagameGroupService.setGroupSpeaker(game, "B", player(game, "xxcha"));

        MegagameGroupService.rotate(game);

        assertThat(MegagameGroupService.memberFactions(game, "A")).containsExactly("letnev", "naalu");
        assertThat(MegagameGroupService.memberFactions(game, "C")).containsExactly("jolnar", "xxcha");
        assertThat(MegagameGroupService.groupSpeaker(game, "A").getFaction()).isEqualTo("naalu");
        assertThat(game.getSpeaker().getFaction()).isEqualTo("xxcha");
        assertThat(MegagameGroupService.orderFromSpeaker(game, "C"))
                .extracting(Player::getFaction)
                .containsExactly("xxcha", "jolnar");
    }

    @Test
    void nextRoundPreviewChangesNothingAndMatchesTheRealRotation() {
        Game game = threeGroups();
        MegagameGroupService.setGroupSpeaker(game, "B", player(game, "xxcha"));

        MegagameGroupService.Layout preview = MegagameGroupService.nextRoundLayout(game);

        assertThat(MegagameGroupService.memberFactions(game, "A")).containsExactly("sol", "hacan");
        assertThat(game.getSpeaker().getFaction()).isEqualTo("naalu");
        MegagameGroupService.rotate(game);
        for (String group : MegagameGroupService.groups(game)) {
            assertThat(preview.members().get(group)).isEqualTo(MegagameGroupService.members(game, group));
            assertThat(preview.speakers().get(group)).isEqualTo(MegagameGroupService.groupSpeaker(game, group));
        }
    }

    @Test
    void politicalStabilityCardFollowsItsHolderIntoTheNewGroup() {
        Game game = threeGroups();
        player(game, "sol").addSC(51);
        game.setStoredValue("politicalStabilityFaction", "sol");

        MegagameGroupService.rotate(game);

        assertThat(player(game, "sol").getSCs()).containsExactly(52);
    }

    @Test
    void movingTheGroupSpeakerHandsTheTokenToTheNextMember() {
        Game game = threeGroups();

        MegagameGroupService.movePlayer(game, player(game, "sol"), "B", 1);

        assertThat(MegagameGroupService.memberFactions(game, "B")).containsExactly("sol", "jolnar", "xxcha");
        assertThat(MegagameGroupService.groupSpeaker(game, "A").getFaction()).isEqualTo("hacan");
    }

    @Test
    void movingWithinTheOwnGroupKeepsTheSpeakerAndUnknownGroupsAreRefused() {
        Game game = threeGroups();

        MegagameGroupService.movePlayer(game, player(game, "sol"), "A", 2);
        String refusal = MegagameGroupService.movePlayer(game, player(game, "sol"), "D", null);

        assertThat(MegagameGroupService.memberFactions(game, "A")).containsExactly("hacan", "sol");
        assertThat(MegagameGroupService.groupSpeaker(game, "A").getFaction()).isEqualTo("sol");
        assertThat(refusal).contains("not in use");
        assertThat(MegagameGroupService.groups(game)).containsExactly("A", "B", "C");
    }

    @Test
    void activeOnlyWithTagAndGroupsAndOutsideFog() {
        Game game = threeGroups();
        assertThat(MegagameGroupService.isActive(game)).isTrue();

        game.getTags().clear();
        assertThat(MegagameGroupService.isActive(game)).isFalse();

        game.addTag(MegagameGroupService.SC_GROUPS_TAG);
        game.setFowMode(true);
        assertThat(MegagameGroupService.isActive(game)).isFalse();

        game.setFowMode(false);
        MegagameGroupService.clearGroupState(game);
        assertThat(MegagameGroupService.isActive(game)).isFalse();
    }

    static Game threeGroups() {
        Game game = gameWithCards(11, 21, 12, 22, 13, 23);
        game.setSpeaker(player(game, "naalu"));
        MegagameGroupService.initGroups(game);
        FACTIONS.forEach(faction -> player(game, faction).clearSCs());
        game.addTag(MegagameGroupService.SC_GROUPS_TAG);
        return game;
    }

    static Game gameWithCards(int... heldCards) {
        Game game = new Game();
        game.newGameSetup();
        game.setTags(new ArrayList<>());
        game.setStrategyCardSet("pbd30k");
        for (int i = 0; i < FACTIONS.size(); i++) {
            Player player = game.addPlayer("user" + i, "User " + i);
            player.setFaction(FACTIONS.get(i));
            player.setColor(COLOURS.get(i));
            if (i < heldCards.length) {
                player.addSC(heldCards[i]);
            }
        }
        return game;
    }

    static Player player(Game game, String faction) {
        return game.getPlayerFromColorOrFaction(faction);
    }
}
