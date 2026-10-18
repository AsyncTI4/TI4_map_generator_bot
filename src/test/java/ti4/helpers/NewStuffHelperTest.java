package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;

/**
 * Covers {@code paginateWithPinnedButtons} only - {@code buttonPagination}, which the rest of the bot uses,
 * is deliberately left alone.
 *
 * <p>Pagination has to respect Discord's 25-buttons-per-message cap while spending as few pages as possible.
 * Sizing every page for BOTH nav buttons is what goes wrong: the first page has no "Previous" and the last no
 * "Next", so it wastes a slot per edge page and can invent a whole extra page - 47 buttons at a cap of 25
 * would come out as three pages, the last holding a single button.
 */
class NewStuffHelperTest {

    private static final String PREFIX = "somePrefix_";

    private static List<Button> buttons(int count) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            buttons.add(Buttons.gray(PREFIX + "target" + i, "Target " + i));
        }
        return buttons;
    }

    private static boolean isNav(Button button) {
        return button.getCustomId() != null && button.getCustomId().startsWith(PREFIX + "page");
    }

    private static List<Button> contentOf(List<Button> page) {
        return page.stream().filter(b -> !isNav(b)).toList();
    }

    private static List<Button> pageOf(List<Button> all, int page, int capacity) {
        return NewStuffHelper.paginateWithPinnedButtons(all, List.of(), PREFIX, capacity, page);
    }

    /** Walks pages until one carries no "Next", collecting the content buttons in order. */
    private static List<Button> walkAllPages(List<Button> all, int capacity) {
        List<Button> seen = new ArrayList<>();
        int page = 0;
        while (true) {
            List<Button> rendered = pageOf(all, page, capacity);
            assertThat(rendered).hasSizeLessThanOrEqualTo(capacity);
            List<Button> content = contentOf(rendered);
            assertThat(content).isNotEmpty();
            seen.addAll(content);
            if (rendered.stream().noneMatch(NewStuffHelperTest::isNextButton)) {
                return seen;
            }
            page++;
        }
    }

    private static boolean isNextButton(Button button) {
        return button.getLabel().startsWith("Next Page");
    }

    @Test
    void fortySevenButtonsFitOnTwoPages() {
        List<Button> all = buttons(47);

        List<Button> firstPage = pageOf(all, 0, 25);
        List<Button> secondPage = pageOf(all, 1, 25);

        assertThat(firstPage).hasSize(25);
        assertThat(contentOf(firstPage)).hasSize(24);
        assertThat(secondPage).hasSize(24);
        assertThat(contentOf(secondPage)).hasSize(23);
        // A third page must not exist: asking for it clamps back to the last page.
        assertThat(pageOf(all, 2, 25)).isEqualTo(secondPage);
    }

    @Test
    void everyPageStaysWithinCapacityAndNoPageIsEmpty() {
        for (int capacity : new int[] {24, 25}) {
            for (int count = 24; count <= 80; count++) {
                List<Button> all = buttons(count);
                assertThat(walkAllPages(all, capacity))
                        .as("capacity %d, %d buttons", capacity, count)
                        .containsExactlyElementsOf(all);
            }
        }
    }

    @Test
    void extraButtonsAppearOnEveryPage() {
        List<Button> all = buttons(60);
        Button pinned = Buttons.red("blindSelection~MDL_x_P", "Blind Target");

        for (int page = 0; page < 3; page++) {
            List<Button> rendered = NewStuffHelper.paginateWithPinnedButtons(all, List.of(pinned), PREFIX, 25, page);
            assertThat(rendered).hasSizeLessThanOrEqualTo(25);
            assertThat(rendered).contains(pinned);
        }
    }

    /** A list short enough to need no nav buttons still has to carry the pinned ones. */
    @Test
    void shortListStillGetsItsPinnedButtons() {
        List<Button> all = buttons(3);
        Button pinned = Buttons.red("blindSelection~MDL_x_P", "Blind Target");

        List<Button> rendered = NewStuffHelper.paginateWithPinnedButtons(all, List.of(pinned), PREFIX, 25, 0);

        assertThat(rendered).hasSize(4);
        assertThat(rendered).contains(pinned);
        assertThat(rendered).noneMatch(NewStuffHelperTest::isNextButton);
    }

    /** Callers pass List.of(...) and .toList() results, so nothing may be appended to the caller's list. */
    @Test
    void immutableInputIsNotMutated() {
        List<Button> all = List.of(Buttons.gray(PREFIX + "only", "Only"));
        Button pinned = Buttons.red("blindSelection~MDL_x_P", "Blind Target");

        List<Button> rendered = NewStuffHelper.paginateWithPinnedButtons(all, List.of(pinned), PREFIX, 25, 0);

        assertThat(all).hasSize(1);
        assertThat(rendered).hasSize(2);
    }

    /**
     * Only one pinned button (Blind Target) exists today, but the capacity math must generalize: nothing
     * hardcodes "1" anywhere in paginateWithPinnedButtons, so this pins that a future second pinned button
     * would still fit on every page without pushing a page over the cap.
     */
    @Test
    void multiplePinnedButtonsAllSurviveEveryPage() {
        List<Button> all = buttons(50);
        List<Button> pinned = List.of(
                Buttons.red("blindSelection~MDL_x_P", "Blind Target"), Buttons.gray("someOtherPinned", "Other"));

        for (int page = 0; page < 3; page++) {
            List<Button> rendered = NewStuffHelper.paginateWithPinnedButtons(all, pinned, PREFIX, 25, page);
            assertThat(rendered).hasSizeLessThanOrEqualTo(25);
            assertThat(rendered).containsAll(pinned);
        }
    }
}
