package ti4.service.leader.agent.modules.homebrew.beans;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersLeadersHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;
import ti4.service.tech.PlayerTechService;

public final class NetrunnersAgent extends AgentModule<NetrunnersAgent.TechSwap> {

    static final String ID = "netrunnersagent";
    private static final String USE_ON_OTHER = "useNetrunnersAgentOnOther";
    private static final String SELECT_TARGET = "netrunnersAgentSelectTarget_";
    private static final String SELECT_SELF_TECH = "netrunnersAgentSelectSelfTech_";
    private static final String SELECT_TARGET_TECH = "netrunnersAgentSelectTargetTech_";
    private static final String GAIN_TECH = "netrunnersAgentGainTech_";
    private static final String SOURCE_TECH_NAV = "netrunnersAgentSourcePage_";
    private static final String GAIN_TECH_NAV = "netrunnersAgentGainPage_";
    private static final String SEPARATOR = "|";
    private static final String AGENT_LABEL = "Zor No-ahn";

    public record TechSwap(Player target, String sourceTech, String gainedTech) {}

    public NetrunnersAgent() {
        super(ID, "Zor No-ahn, the Netrunners");
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                holder.factionButtonChecker() + USE_ON_OTHER,
                useLabel(holder, ID, AGENT_LABEL),
                FactionEmojis.netrunners);
    }

    public static void offerAfterGainingTech(
            GenericInteractionCreateEvent event, Game game, Player holder, String gainedTech) {
        TechnologyModel gained = Mapper.getTech(gainedTech);
        if (!holder.hasUnexhaustedLeader(ID) || gained == null || !isEligibleSourceTechnology(holder, gainedTech)) {
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCorrectChannel(),
                holder.getRepresentationNoPing() + ", you may exhaust Zor No-ahn after gaining "
                        + gained.getNameRepresentation() + ".",
                List.of(
                        Buttons.gray(
                                holder.factionButtonChecker() + SELECT_SELF_TECH + gainedTech,
                                useLabel(holder, ID, AGENT_LABEL),
                                FactionEmojis.netrunners),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(USE_ON_OTHER)
    static void useOnOther(ButtonInteractionEvent event, Game game, Player holder) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        List<Button> buttons = game.getRealPlayersExcludingThis(holder).stream()
                .filter(NetrunnersAgent::hasEligibleSourceTechnology)
                .map(target -> Buttons.gray(
                        holder.factionButtonChecker() + SELECT_TARGET + target.getFaction(), target.getUserName()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "There are no eligible players to target.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                holder.getRepresentationNoPing() + ", choose a player for Zor No-ahn.",
                buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(SELECT_TARGET)
    static void selectTarget(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_TARGET.length()));
        if (target == null || target == holder || !hasEligibleSourceTechnology(target)) {
            return;
        }
        NewStuffHelper.sendPaginated(
                target.getCardsInfoThread(),
                sourceTechMessage(target),
                sourceTechButtons(holder, target),
                List.of(),
                target.factionButtonChecker() + SOURCE_TECH_NAV + holder.getFaction() + SEPARATOR);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_SELF_TECH)
    static void selectSelfTech(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        String sourceTech = buttonID.substring(SELECT_SELF_TECH.length());
        if (!holder.hasUnexhaustedLeader(ID) || !ownsEligibleSourceTechnology(holder, sourceTech)) {
            return;
        }
        sendGainTechButtons(event, game, holder, holder, sourceTech);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TARGET_TECH)
    static void selectTargetTech(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(SELECT_TARGET_TECH.length()).split("\\|", 2);
        Player holder = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (holder == null || !holder.hasUnexhaustedLeader(ID) || !ownsEligibleSourceTechnology(target, payload[1])) {
            return;
        }
        sendGainTechButtons(event, game, holder, target, payload[1]);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SOURCE_TECH_NAV)
    static void turnSourceTechPage(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String holderFaction = buttonID.substring(SOURCE_TECH_NAV.length()).replaceFirst("\\|page\\d+$", "");
        Player holder = game.getPlayerFromColorOrFaction(holderFaction);
        if (holder == null || !holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        NewStuffHelper.turnPage(
                event,
                buttonID,
                sourceTechMessage(target),
                () -> sourceTechButtons(holder, target),
                List.of(),
                target.factionButtonChecker() + SOURCE_TECH_NAV + holderFaction + SEPARATOR);
    }

    @ButtonHandler(GAIN_TECH)
    static void gainTech(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(GAIN_TECH.length()).split("\\|", 3);
        Player holder = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (holder == null || !holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        String swap = String.join(SEPARATOR, target.getFaction(), payload[1], payload[2]);
        if (AgentLifecycle.use(game, holder, ID, swap, event)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler(GAIN_TECH_NAV)
    static void turnGainTechPage(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(GAIN_TECH_NAV.length()).split("\\|", 3);
        Player holder = payload.length == 3 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (holder == null || !holder.hasUnexhaustedLeader(ID) || !ownsEligibleSourceTechnology(target, payload[1])) {
            return;
        }
        String sourceTech = payload[1];
        NewStuffHelper.turnPage(
                event,
                buttonID,
                gainTechMessage(target),
                () -> gainTechButtons(game, holder, target, sourceTech),
                List.of(),
                gainTechNavPrefix(holder, target, sourceTech));
    }

    @Override
    public Optional<TechSwap> decode(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.empty();
        }
        String[] parts = payload.split("\\|");
        if (parts.length != 3) {
            return Optional.empty();
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        if (target == null || Mapper.getTech(parts[1]) == null || Mapper.getTech(parts[2]) == null) {
            return Optional.empty();
        }
        return Optional.of(new TechSwap(target, parts[1], parts[2]));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, TechSwap swap) {
        Player target = swap.target();
        if (!ownsEligibleSourceTechnology(target, swap.sourceTech())) {
            return Optional.of(target.getRepresentationNoPing() + " cannot return that technology");
        }
        if (eligibleGains(game, target, swap.sourceTech()).stream()
                .noneMatch(tech -> tech.getAlias().equals(swap.gainedTech()))) {
            return Optional.of(target.getRepresentationNoPing() + " cannot gain that technology");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<TechSwap> use) {
        TechSwap swap = use.payload();
        Player target = swap.target();
        target.removeTech(swap.sourceTech());
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + " returned "
                        + Mapper.getTech(swap.sourceTech()).getNameRepresentation()
                        + " to the technology deck due to " + use.agentNameFor(target) + ".");
        PlayerTechService.addTech(use.event(), use.game(), target, swap.gainedTech());
        return AgentOutcome.none();
    }

    private static void sendGainTechButtons(
            ButtonInteractionEvent event, Game game, Player holder, Player target, String sourceTech) {
        if (eligibleGains(game, target, sourceTech).isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "There is no eligible technology for Zor No-ahn to grant.");
            return;
        }
        NewStuffHelper.sendPaginated(
                target.getCorrectChannel(),
                gainTechMessage(target),
                gainTechButtons(game, holder, target, sourceTech),
                List.of(),
                gainTechNavPrefix(holder, target, sourceTech));
    }

    private static String gainTechNavPrefix(Player holder, Player target, String sourceTech) {
        return target.factionButtonChecker() + GAIN_TECH_NAV + holder.getFaction() + SEPARATOR + sourceTech + SEPARATOR;
    }

    static List<Button> sourceTechButtons(Player holder, Player target) {
        return target.getTechs().stream()
                .filter(tech -> isEligibleSourceTechnology(target, tech))
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .map(tech -> NetrunnersLeadersHandler.techButton(
                        target.factionButtonChecker()
                                + SELECT_TARGET_TECH
                                + holder.getFaction()
                                + SEPARATOR
                                + tech.getAlias(),
                        tech))
                .toList();
    }

    static List<Button> gainTechButtons(Game game, Player holder, Player target, String sourceTech) {
        return eligibleGains(game, target, sourceTech).stream()
                .map(tech -> NetrunnersLeadersHandler.techButton(
                        target.factionButtonChecker()
                                + GAIN_TECH
                                + holder.getFaction()
                                + SEPARATOR
                                + sourceTech
                                + SEPARATOR
                                + tech.getAlias(),
                        tech))
                .toList();
    }

    private static String sourceTechMessage(Player target) {
        return target.getRepresentationNoPing()
                + ", choose the technology Zor No-ahn will return to the technology deck.";
    }

    private static String gainTechMessage(Player target) {
        return target.getRepresentationNoPing() + ", choose the technology to gain with Zor No-ahn.";
    }

    static List<TechnologyModel> eligibleGains(Game game, Player target, String sourceTech) {
        TechnologyModel source = Mapper.getTech(sourceTech);
        if (source == null) {
            return List.of();
        }
        int requirementCount = source.getRequirements().orElse("").length();
        return game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech ->
                        tech.getFaction().map(target.getFaction()::equals).orElse(true))
                .filter(tech -> !target.hasTech(tech.getAlias()))
                .filter(tech -> tech.getRequirements().orElse("").length() == requirementCount)
                .filter(tech -> Collections.disjoint(tech.getTypes(), source.getTypes()))
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .toList();
    }

    private static boolean hasEligibleSourceTechnology(Player target) {
        return target.getTechs().stream().anyMatch(tech -> isEligibleSourceTechnology(target, tech));
    }

    private static boolean ownsEligibleSourceTechnology(Player player, String tech) {
        return isEligibleSourceTechnology(player, tech) && player.hasTech(tech);
    }

    private static boolean isEligibleSourceTechnology(Player player, String tech) {
        TechnologyModel technology = Mapper.getTech(tech);
        return technology != null
                && technology.getFaction().map(player.getFaction()::equals).orElse(true);
    }
}
