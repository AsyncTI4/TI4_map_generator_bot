package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.tech.ListTechService;

public final class ArcanumAgent extends AgentModule<ArcanumAgent.TechSwap> {

    static final String ID = "arcanumagent";
    private static final String USE_ON_SELF = "arcanumVeylaUseSelf";
    private static final String USE_ON_OTHER = "arcanumVeylaUseOnOther";
    private static final String CHOOSE_TARGET = "arcanumVeylaTarget_";
    private static final String SELECT_RETURN_TECH = "arcanumVeylaReturnTech_";
    private static final String SEPARATOR = "|";
    private static final String SETUP_PHASE = "setup";
    private static final int BUTTONS_PER_MESSAGE = 25;

    public record TechSwap(Player target, TechnologyModel returnedTech) {}

    public ArcanumAgent() {
        super(ID, "Veyla Biphos, the Arcanum");
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                holder.factionButtonChecker() + USE_ON_OTHER,
                useLabel(holder, ID, "Arcanum Agent", " on Another Player"));
    }

    public static Button startTurnButton(Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasUnexhaustedLeader(ID)
                || returnTechButtons(game, player, player).isEmpty()) {
            return null;
        }
        return Buttons.gray(
                player.factionButtonChecker() + USE_ON_SELF,
                useLabel(player, ID, "Arcanum Agent"),
                FactionEmojis.arcanum);
    }

    @ButtonHandler(USE_ON_SELF)
    static void useOnSelf(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> returnTechButtons = returnTechButtons(game, player, player);
        if (!player.hasUnexhaustedLeader(ID) || returnTechButtons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        String message = player.getRepresentation()
                + ", choose a non-faction, non-unit technology to return with _Veyla, the Arcanum agent_.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), message, firstPage(returnTechButtons, returnTechPrefix(player, player)));
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(USE_ON_OTHER)
    static void offerTargets(ButtonInteractionEvent event, Game game, Player holder) {
        if (SETUP_PHASE.equalsIgnoreCase(game.getPhaseOfGame()) || !holder.hasUnexhaustedLeader(ID)) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
            return;
        }
        List<Button> targetButtons = game.getRealPlayers().stream()
                .filter(target -> target != holder)
                .filter(target -> !returnTechButtons(game, holder, target).isEmpty())
                .map(target -> Buttons.green(
                        holder.factionButtonChecker() + CHOOSE_TARGET + target.getFaction(),
                        target.getFactionNameOrColor(),
                        target.getFactionEmojiOrColor()))
                .toList();
        if (targetButtons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No other player has an eligible technology to return.");
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                holder.getCardsInfoThread(),
                holder.getRepresentationUnfogged() + ", choose a player to use _Veyla, the Arcanum agent_ on.",
                targetButtons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(CHOOSE_TARGET)
    static void chooseTarget(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        if (SETUP_PHASE.equalsIgnoreCase(game.getPhaseOfGame()) || !holder.hasUnexhaustedLeader(ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(CHOOSE_TARGET.length()));
        List<Button> returnTechButtons = returnTechButtons(game, holder, target);
        if (target == null || returnTechButtons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (returnTechButtons.size() == 1) {
            swap(event, game, holder, target, returnedTechId(returnTechButtons.getFirst()));
            return;
        }
        String message = target.getRepresentation()
                + ", _Veyla, the Arcanum agent_ is being used on you. Choose a non-faction technology to return.";
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(), message, firstPage(returnTechButtons, returnTechPrefix(holder, target)));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_RETURN_TECH)
    static void selectTechToReturn(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String[] payload = buttonID.substring(SELECT_RETURN_TECH.length()).split("\\|", 2);
        Player holder = payload.length == 2 ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (holder == null || !holder.hasUnexhaustedLeader(ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = target.getRepresentation()
                + ", _Veyla, the Arcanum agent_ is being used on you. Choose a non-faction technology to return.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                returnTechButtons(game, holder, target),
                message,
                returnTechPrefix(holder, target),
                buttonID)) {
            return;
        }
        swap(event, game, holder, target, payload[1]);
    }

    private static void swap(
            ButtonInteractionEvent event, Game game, Player holder, Player target, String returnedTechId) {
        if (AgentLifecycle.use(game, holder, ID, target.getFaction() + SEPARATOR + returnedTechId, event)) {
            ButtonHelper.deleteMessage(event);
        }
    }

    @Override
    public Optional<TechSwap> decode(Game game, Player user, String payload) {
        String[] parts = StringUtils.split(payload, SEPARATOR);
        if (parts == null || parts.length != 2) {
            return Optional.empty();
        }
        Player target = game.getPlayerFromColorOrFaction(parts[0]);
        TechnologyModel returnedTech = Mapper.getTech(parts[1]);
        if (target == null || returnedTech == null) {
            return Optional.empty();
        }
        return Optional.of(new TechSwap(target, returnedTech));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, TechSwap swap) {
        if (SETUP_PHASE.equalsIgnoreCase(game.getPhaseOfGame())) {
            return Optional.of("Veyla Biphos cannot be used during setup");
        }
        TechnologyModel tech = swap.returnedTech();
        if (tech.isFactionTech()
                || tech.isUnitUpgrade()
                || !swap.target().hasTech(tech.getAlias())
                || replacementTechs(game, swap.target(), tech).isEmpty()) {
            return Optional.of(tech.getName() + " cannot be returned for Veyla Biphos");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<TechSwap> use) {
        Player target = use.payload().target();
        TechnologyModel returnedTech = use.payload().returnedTech();
        String returnedTechId = returnedTech.getAlias();
        target.removeTech(returnedTechId);
        List<TechnologyModel> replacementTechs = replacementTechs(use.game(), target, returnedTech);
        return AgentOutcome.of(Message.addressedTo(
                target,
                ", " + use.agentNameFor(target) + " returned " + returnedTech.getNameRepresentation()
                        + " to the technology deck. Research a technology of a different color.",
                ListTechService.getTechButtons(new ArrayList<>(replacementTechs), target, "free")));
    }

    private static List<Button> firstPage(List<Button> buttons, String prefix) {
        return buttons.size() <= BUTTONS_PER_MESSAGE ? buttons : NewStuffHelper.buttonPagination(buttons, prefix, 0);
    }

    private static String returnTechPrefix(Player holder, Player target) {
        return target.factionButtonChecker() + SELECT_RETURN_TECH + holder.getFaction() + SEPARATOR;
    }

    static List<TechnologyModel> replacementTechs(Game game, Player target, TechnologyModel returnedTech) {
        if (game == null || target == null || returnedTech == null) {
            return List.of();
        }
        return TechnologyType.mainFour.stream()
                .filter(type -> !returnedTech.getTypes().contains(type))
                .flatMap(type -> ListTechService.getAllTechOfAType(game, type.toString(), target, false, true).stream())
                .filter(tech -> game.getTechnologyDeck().contains(tech.getAlias()))
                .distinct()
                .toList();
    }

    static List<Button> returnTechButtons(Game game, Player holder, Player target) {
        if (game == null || holder == null || target == null) {
            return List.of();
        }
        return target.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> !tech.isFactionTech())
                .filter(tech -> !tech.isUnitUpgrade())
                .filter(tech -> !replacementTechs(game, target, tech).isEmpty())
                .map(tech -> Buttons.green(
                        returnTechPrefix(holder, target) + tech.getAlias(),
                        "Return " + tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static String returnedTechId(Button button) {
        return StringUtils.substringAfterLast(button.getCustomId(), SEPARATOR);
    }
}
