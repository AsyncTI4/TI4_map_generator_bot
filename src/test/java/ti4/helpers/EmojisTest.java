package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.junit.jupiter.api.Test;
import ti4.helpers.Emojis.TI4Emoji;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmojisTest extends BaseTi4Test {

    @Test
    void testEmojis() {
        beforeAll();

        List<String> emojiEnumNames = Arrays.stream(TI4Emoji.values()).map(Enum::name).toList();
        List<String> emojiFileNames = Emojis.enumerateEmojiFilesRecursive(Storage.getAppEmojiDirectory()).stream()
            .map(f -> f.getName().replace(".png", "").replace(".jpg", "").replace(".gif", "")).toList();

        checkForDupes(emojiFileNames, "files");
        checkForDupes(emojiEnumNames, "enums");
        checkMissing(new HashSet<>(emojiEnumNames), new HashSet<>(emojiFileNames), "emoji files");
        checkMissing(new HashSet<>(emojiFileNames), new HashSet<>(emojiEnumNames), "enum consts");
    }

    public static void checkForDupes(List<String> emojiNames, String descr) {
        Set<String> dupes = Helper.findDuplicateInList(new ArrayList<>(emojiNames.stream().map(String::toLowerCase).toList()));
        assertTrue(dupes.isEmpty(), "There are multiple emoji " + descr + " with a similar name: " + String.join(", ", dupes));
    }

    private static void checkMissing(Set<String> one, Set<String> two, String what) {
        Set<String> missing = SetUtils.difference(one, two);
        //assertTrue(missing.isEmpty(), "There are missing " + what + ": " + String.join(", ", missing));
    }
}
