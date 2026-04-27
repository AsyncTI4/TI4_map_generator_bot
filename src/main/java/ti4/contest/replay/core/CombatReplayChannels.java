package ti4.contest.replay.core;

import lombok.experimental.UtilityClass;

/**
 * Centralizes Discord channel naming for replay contest versions.
 */
@UtilityClass
public class CombatReplayChannels {

    private static final String CONTEST_CHANNEL_NAME_V1 = "lazax-war-archives-dev";
    private static final String CONTEST_CHANNEL_NAME_V2 = "lazax-war-archives";

    public String contestChannelName(CombatContestSettings settings) {
        return isReplayV2Enabled(settings) ? CONTEST_CHANNEL_NAME_V2 : CONTEST_CHANNEL_NAME_V1;
    }

    private boolean isReplayV2Enabled(CombatContestSettings settings) {
        return "v2".equalsIgnoreCase(settings.getRuntime().getVersionEnabled());
    }
}
