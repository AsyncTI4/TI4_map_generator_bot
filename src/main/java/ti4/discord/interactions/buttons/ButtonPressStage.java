package ti4.discord.interactions.buttons;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
enum ButtonPressStage {
    GATEWAY("gateway", "Discord → bot", true),
    HANDOFF("handoff", "Waited for worker", true),
    CONTEXT("context", "Built context", false),
    LOG("log", "Logged", false),
    REPLAY_SNAPSHOT("replay-snap", "Captured combat replay snapshot", false),
    RESOLVE("resolve", "Executed", false),
    SAVE("save", "Saved", false),
    REPLAY_SETTLE("replay-settle", "Settled combat replay", false);

    private final String shortName;
    private final String description;
    private final boolean preprocessing;
}
