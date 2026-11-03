package ti4.image.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class LawsAndEventsSectionTest extends BaseTi4Test {

    private static final int LAW_ROW_GAP = 8;
    private static final int BLOCK_BOTTOM_MARGIN = 3;
    private static final String SHORT_LAW = "nexus";
    private static final String LONG_LAW = "absol_minspolicy";

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
    }

    @Test
    void twoShortCardsMakeARowOfTheStandardHeight() {
        assertEquals(List.of(CardBox.HEIGHT), LawsAndEventsSection.rowHeights(List.of(110, 110)));
    }

    @Test
    void aShortCardNextToALongCardTakesTheLongCardsHeight() {
        assertEquals(List.of(188), LawsAndEventsSection.rowHeights(List.of(110, 188)));
        assertEquals(List.of(188), LawsAndEventsSection.rowHeights(List.of(188, 110)));
    }

    @Test
    void eachRowIsSizedByItsOwnCards() {
        assertEquals(List.of(110, 162, 110), LawsAndEventsSection.rowHeights(List.of(110, 110, 136, 162, 110)));
    }

    @Test
    void aLoneCardInTheLastRowKeepsItsOwnHeight() {
        assertEquals(List.of(110, 136), LawsAndEventsSection.rowHeights(List.of(110, 110, 136)));
    }

    @Test
    void noCardsMakeNoRows() {
        assertEquals(List.of(), LawsAndEventsSection.rowHeights(List.of()));
    }

    @Test
    void theBlockHeightAddsTheGapAfterEveryRowAndTheBottomMargin() {
        assertEquals(
                110 + LAW_ROW_GAP + 136 + LAW_ROW_GAP + BLOCK_BOTTOM_MARGIN,
                LawsAndEventsSection.blockHeight(List.of(110, 136), LAW_ROW_GAP));
        assertEquals(BLOCK_BOTTOM_MARGIN, LawsAndEventsSection.blockHeight(List.of(), LAW_ROW_GAP));
    }

    @Test
    void twoShortLawsStayOnOneStandardRow() {
        game.addLaw(SHORT_LAW, null);
        game.addLaw("minister_peace", null);

        assertEquals(CardBox.HEIGHT + LAW_ROW_GAP + BLOCK_BOTTOM_MARGIN, LawsAndEventsSection.heightOfLaws(game));
    }

    @Test
    void aLongLawGrowsItsRowAndTheLawBlock() {
        game.addLaw(SHORT_LAW, null);
        game.addLaw(LONG_LAW, null);

        int shortRow = CardBox.HEIGHT + LAW_ROW_GAP + BLOCK_BOTTOM_MARGIN;
        assertTrue(LawsAndEventsSection.heightOfLaws(game) > shortRow);
    }

    @Test
    void aLongLawInTheSecondRowLeavesTheFirstRowAlone() {
        game.addLaw(SHORT_LAW, null);
        game.addLaw("minister_peace", null);
        game.addLaw(LONG_LAW, null);

        int twoShortRows = 2 * (CardBox.HEIGHT + LAW_ROW_GAP) + BLOCK_BOTTOM_MARGIN;
        assertTrue(LawsAndEventsSection.heightOfLaws(game) > twoShortRows);
    }

    @Test
    void noLawsAndNoEventsOnlyHaveTheBottomMargin() {
        assertEquals(BLOCK_BOTTOM_MARGIN, LawsAndEventsSection.heightOfLaws(game));
        assertEquals(BLOCK_BOTTOM_MARGIN, LawsAndEventsSection.heightOfEvents(game));
    }
}
