package ti4.service.emoji;

import org.jetbrains.annotations.NotNull;

public class EmojiService {

    @NotNull
    public static String getFactionIconFromDiscord(String faction) {
        return FactionEmojis.getFactionIconFromDiscord(faction);
    }

}
