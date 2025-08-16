package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections4.SetUtils;
import org.junit.jupiter.api.Test;
import ti4.service.emoji.ApplicationEmojiService;
import ti4.service.emoji.ApplicationEmojiService.EmojiFileData;
import ti4.service.emoji.TI4Emoji;
import ti4.testUtils.BaseTi4Test;

class EmojisTest extends BaseTi4Test {

    @Test
    void testEmojis() {
        List<String> emojiEnumNames =
                TI4Emoji.allEmojiEnums().stream().map(TI4Emoji::name).toList();
        List<String> emojiFileNames = ApplicationEmojiService.enumerateEmojiFilesRecursive()
                .map(EmojiFileData::new)
                .map(EmojiFileData::getName)
                .toList();

        checkForDupes(emojiFileNames, "files");
        checkForDupes(emojiEnumNames, "enums");
        checkMissing(new HashSet<>(emojiEnumNames), new HashSet<>(emojiFileNames), "emoji files");
        verifyCount(emojiEnumNames.size());

        // Don't need to check this, generally. If there's a new file that someone wants to use, they can figure out how
        // to use it :)
        // checkMissing(new HashSet<>(emojiFileNames), new HashSet<>(emojiEnumNames), "enum consts");
    }

    private static void checkForDupes(List<String> emojiNames, String descr) {
        Set<String> dupes = Helper.findDuplicateInList(
                new ArrayList<>(emojiNames.stream().map(String::toLowerCase).toList()));

        String error = "There are multiple emoji " + descr + " with a similar name: " + String.join(", ", dupes);
        error += "\n- Please remove the duplicative files.\n";
        assertTrue(dupes.isEmpty(), error);
    }

    private static void checkMissing(Set<String> one, Set<String> two, String what) {
        Set<String> missing = SetUtils.difference(one, two);
        assertTrue(missing.isEmpty(), "There are missing " + what + ": " + String.join(", ", missing));
    }

    private static void verifyCount(int numberUsed) {
        assertTrue(numberUsed <= 2000, "We can only have 2000 application emojis");
    }
}
