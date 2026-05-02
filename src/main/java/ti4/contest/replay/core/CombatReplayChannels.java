package ti4.contest.replay.core;

import lombok.experimental.UtilityClass;

/**
 * Centralizes Discord channel naming for replay contest versions.
 */
@UtilityClass
public class CombatReplayChannels {

    private static final String CONTEST_CHANNEL_NAME_V2 = "lazax-war-archives";

    public String contestChannelName(CombatContestSettings settings) {
        return CONTEST_CHANNEL_NAME_V2;
    }
}
