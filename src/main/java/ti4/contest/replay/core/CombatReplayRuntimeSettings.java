package ti4.contest.replay.core;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CombatReplayRuntimeSettings {
    public static final boolean SHADOW_MODE = true;

    private static volatile boolean discordPostingEnabled = false;

    public static boolean isDiscordPostingEnabled() {
        return discordPostingEnabled;
    }

    public static boolean toggleDiscordPostingEnabled() {
        discordPostingEnabled = !discordPostingEnabled;
        return discordPostingEnabled;
    }
}
