package ti4.service.leader.agent.modules;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class CabalAgent extends TargetedAgent {

    static final String ID = "cabalagent";
    private static final String CAPTURE_PREFIX = "cabalAgentCapture_";
    private static final String LEGACY_PAYLOAD_PREFIX = "startCabalAgent_";

    private record Capturable(String unit, UnitEmojis emoji, int cost, int boardLimit) {}

    private static final List<Capturable> CAPTURABLE = List.of(
            new Capturable("destroyer", UnitEmojis.destroyer, 1, 8),
            new Capturable("fighter", UnitEmojis.fighter, 1, Integer.MAX_VALUE),
            new Capturable("infantry", UnitEmojis.infantry, 1, Integer.MAX_VALUE),
            new Capturable("mech", UnitEmojis.mech, 2, 4),
            new Capturable("cruiser", UnitEmojis.cruiser, 2, 8),
            new Capturable("carrier", UnitEmojis.carrier, 3, 4),
            new Capturable("dreadnought", UnitEmojis.dreadnought, 4, 5),
            new Capturable("flagship", UnitEmojis.flagship, 8, 1));

    public CabalAgent() {
        super(ID, "The Stillness of Stars, the Vuil'raith");
    }

    public static Button button(Player cabal, Player replenisher) {
        return Buttons.green(
                ownedButtonId(cabal, ID, replenisher.getFaction()),
                useLabel(cabal, ID, "Vuil'raith Agent"),
                FactionEmojis.Cabal);
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, StringUtils.removeStart(payload, LEGACY_PAYLOAD_PREFIX));
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<Player> use) {
        return " has exhausted " + use.agentName() + " on " + use.payload().getFactionEmojiOrColor() + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        return AgentOutcome.offer(
                        Message.addressedTo(
                                use.user(),
                                ", please choose which unit you wish to capture.",
                                captureButtons(use, use.payload())),
                        Message.addressedTo(use.user(), ", there is no unit you can capture."))
                .deletingPressedMessage();
    }

    private static List<Button> captureButtons(AgentUse<Player> use, Player victim) {
        int commodityValue = victim.getCommoditiesTotal();
        return CAPTURABLE.stream()
                .filter(unit -> unit.cost() <= commodityValue)
                .filter(unit ->
                        ButtonHelper.getNumberOfUnitsOnTheBoard(use.game(), victim, unit.unit()) < unit.boardLimit())
                .map(unit -> Buttons.red(
                        use.user().factionButtonChecker()
                                + AgentStepIds.flagCopy(
                                        CAPTURE_PREFIX + unit.unit() + "_" + victim.getFaction(), use.viaYssaril()),
                        "Capture " + unit.unit(),
                        unit.emoji()))
                .toList();
    }
}
