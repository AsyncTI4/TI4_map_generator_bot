package ti4.service.emoji;

import org.jetbrains.annotations.NotNull;

final class EmojiService {

    @NotNull
    public static String getFactionIconFromDiscord(String faction) {
        return FactionEmojis.getFactionIcon(faction).toString();
    }
}
