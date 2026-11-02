package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornLeadersHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public final class HazorusAgent extends AgentModule<HazorusAgent.Purge> {

    static final String DORMANT = "stonebornagentdormant";
    static final String UNLEASHED = "stonebornagentunleashed";
    private static final List<String> VERSIONS = List.of(DORMANT, UNLEASHED);
    private static final String SELECT_TARGET = "selectHazorusTarget";
    private static final String LEGACY_USE_ON_SELF = "useHazorusOnSelf_";
    private static final String LEGACY_USE_ON_OTHER = "useHazorusOnOther_";
    private static final String SEPARATOR = "|";

    public record Purge(Player target, String version) {}

    public HazorusAgent() {
        super(DORMANT, "Hazorus, the Stoneborn");
    }

    @Override
    public Set<String> aliasIds() {
        return Set.of(UNLEASHED);
    }

    public static Button startTurnButton(Player player) {
        String version = readyVersion(player);
        if (version == null || player.getFragments().isEmpty()) {
            return null;
        }
        return Buttons.gray(
                ownedButtonId(player, version, payload(player, version)),
                useLabel(player, version, "Hazorus Agent"),
                FactionEmojis.stoneborn);
    }

    public static Button cardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + SELECT_TARGET,
                useLabel(player, DORMANT, "Hazorus Agent", " on Another Player"),
                FactionEmojis.stoneborn);
    }

    @ButtonHandler(SELECT_TARGET)
    static void selectTarget(Game game, Player player, ButtonInteractionEvent event) {
        String version = readyVersion(player);
        if (version == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel(),
                player.getRepresentationNoPing() + ", choose a player for Hazorus.",
                targetButtons(game, player, version));
        ButtonHelper.deleteTheOneButton(event);
    }

    static List<Button> targetButtons(Game game, Player holder, String version) {
        return game.getRealPlayersExcludingThis(holder).stream()
                .map(target -> FoWHelper.fogSafeTargetButton(
                        ownedButtonId(holder, version, payload(target, version)), "gray", target, holder))
                .toList();
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE_ON_SELF)
    static void legacyUseOnSelf(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String version = buttonID.substring(LEGACY_USE_ON_SELF.length());
        if (!VERSIONS.contains(version)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AgentLifecycle.pressLegacyButton(event, game, player, version, payload(player, version));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE_ON_OTHER)
    static void legacyUseOnOther(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(LEGACY_USE_ON_OTHER.length()));
        String version = readyVersion(player);
        if (target == null || version == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        AgentLifecycle.pressLegacyButton(event, game, player, version, payload(target, version));
    }

    private static String payload(Player target, String version) {
        return target.getFaction() + SEPARATOR + version;
    }

    private static String readyVersion(Player player) {
        return VERSIONS.stream()
                .filter(player::hasUnexhaustedLeader)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Optional<Purge> decode(Game game, Player user, String payload) {
        String version = StringUtils.substringAfterLast(payload, SEPARATOR);
        Player target = game.getPlayerFromColorOrFaction(StringUtils.substringBeforeLast(payload, SEPARATOR));
        if (target == null || !VERSIONS.contains(version)) {
            return Optional.empty();
        }
        return Optional.of(new Purge(target, version));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Purge purge) {
        if (purge.target() == user && user.getFragments().isEmpty()) {
            return Optional.of("You have no relic fragments to purge");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Purge> use) {
        StonebornLeadersHandler.offerHazorusFragmentPurge(
                use.game(), use.user(), use.payload().target(), use.payload().version());
        return AgentOutcome.none();
    }
}
