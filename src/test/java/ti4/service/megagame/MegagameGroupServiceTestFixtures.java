package ti4.service.megagame;

import ti4.game.Game;

public final class MegagameGroupServiceTestFixtures {

    private MegagameGroupServiceTestFixtures() {}

    public static Game threeGroupsHoldingCards() {
        Game game = MegagameGroupServiceTest.threeGroups();
        int[] cards = {11, 21, 12, 22, 13, 23};
        for (int i = 0; i < cards.length; i++) {
            MegagameGroupServiceTest.player(game, MegagameGroupServiceTest.FACTIONS.get(i))
                    .addSC(cards[i]);
        }
        game.setSCPlayed(11, true);
        return game;
    }
}
