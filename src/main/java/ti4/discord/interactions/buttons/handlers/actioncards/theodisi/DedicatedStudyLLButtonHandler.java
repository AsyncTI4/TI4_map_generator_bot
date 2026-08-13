package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class DedicatedStudyLLButtonHandler {
    private static final String RESOLVE = "resolveDedicatedStudy";
    private static final String PURGE = "purgeDedicatedStudy_";
    private static final String RESEARCH = "researchDedicatedStudy_";
    private static final String STATE = "dedicatedStudy_";

    @ButtonHandler(RESOLVE)
    public static void resolveDedicatedStudy(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null
                        && !tech.isFactionTech()
                        && !tech.isUnitUpgrade()
                        && tech.getRequirements().orElse("").length() >= 2
                        && hasResearchPair(
                                game,
                                player,
                                tech.getFirstType().toString(),
                                tech.getRequirements().orElse("").length(),
                                tech.getAlias()))
                .map(tech ->
                        Buttons.red(player.factionButtonChecker() + PURGE + tech.getAlias(), "Purge " + tech.getName()))
                .toList();
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no eligible technology to purge for _Dedicated Study_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose a non-faction, non-unit technology with at least 2 prerequisites to purge.",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + PURGE, 0));
    }

    @ButtonHandler(PURGE)
    public static void purgeDedicatedStudy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null
                        && !tech.isFactionTech()
                        && !tech.isUnitUpgrade()
                        && tech.getRequirements().orElse("").length() >= 2
                        && hasResearchPair(
                                game,
                                player,
                                tech.getFirstType().toString(),
                                tech.getRequirements().orElse("").length(),
                                tech.getAlias()))
                .map(tech ->
                        Buttons.red(player.factionButtonChecker() + PURGE + tech.getAlias(), "Purge " + tech.getName()))
                .toList();
        String message = player.getRepresentationNoPing()
                + ", choose a non-faction, non-unit technology with at least 2 prerequisites to purge.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event, event.getMessageChannel(), buttons, message, player.factionButtonChecker() + PURGE, buttonID)) {
            return;
        }

        String techId = buttonID.substring(PURGE.length());
        TechnologyModel tech = Mapper.getTech(techId);
        if (tech == null
                || !player.hasTech(techId)
                || tech.isFactionTech()
                || tech.isUnitUpgrade()
                || tech.getRequirements().orElse("").length() < 2
                || !hasResearchPair(
                        game,
                        player,
                        tech.getFirstType().toString(),
                        tech.getRequirements().orElse("").length(),
                        techId)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That technology is no longer eligible.");
            return;
        }
        int prerequisites = tech.getRequirements().orElse("").length();
        player.purgeTech(techId);
        game.setStoredValue(STATE + player.getFaction(), tech.getFirstType() + "|" + prerequisites + "|0|0|" + techId);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " purged " + tech.getNameRepresentation()
                        + " for _Dedicated Study_.");
        sendResearchButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESEARCH)
    public static void researchDedicatedStudy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|");
        List<Button> buttons = getResearchButtons(game, player, state);
        String message = getResearchMessage(player, state);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + RESEARCH,
                buttonID)) {
            return;
        }

        String techId = buttonID.substring(RESEARCH.length());
        TechnologyModel tech = Mapper.getTech(techId);
        if (state.length != 5
                || tech == null
                || player.hasTech(techId)
                || tech.isFactionTech()
                || tech.isUnitUpgrade()
                || techId.equals(state[4])
                || !tech.getFirstType().toString().equals(state[0])) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That technology is no longer eligible for _Dedicated Study_.");
            return;
        }
        int required = Integer.parseInt(state[1]);
        int chosen = Integer.parseInt(state[2]);
        int spent = Integer.parseInt(state[3]);
        int techRequirements = tech.getRequirements().orElse("").length();
        if (chosen >= 2
                || !buttons.stream().anyMatch(button -> button.getCustomId().endsWith(RESEARCH + techId))) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "That technology is no longer eligible for _Dedicated Study_.");
            return;
        }
        PlayerTechService.getTech(game, player, event, "getTech_" + techId + "__noPay__comp");
        chosen++;
        spent += techRequirements;
        if (chosen == 2 && spent == required) {
            game.removeStoredValue(STATE + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentationNoPing() + " completed _Dedicated Study_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(
                STATE + player.getFaction(), state[0] + "|" + required + "|" + chosen + "|" + spent + "|" + state[4]);
        sendResearchButtons(event, game, player);
    }

    private static void sendResearchButtons(ButtonInteractionEvent event, Game game, Player player) {
        String[] state = game.getStoredValue(STATE + player.getFaction()).split("\\|");
        if (state.length != 5) return;
        List<Button> buttons = getResearchButtons(game, player, state);
        if (buttons.isEmpty()) {
            game.removeStoredValue(STATE + player.getFaction());
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " cannot complete _Dedicated Study_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                getResearchMessage(player, state),
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + RESEARCH, 0));
    }

    private static List<Button> getResearchButtons(Game game, Player player, String[] state) {
        List<Button> buttons = new ArrayList<>();
        if (state.length != 5) return buttons;
        int required = Integer.parseInt(state[1]);
        int chosen = Integer.parseInt(state[2]);
        int spent = Integer.parseInt(state[3]);
        int remaining = required - spent;
        for (String techId : game.getTechnologyDeck()) {
            TechnologyModel tech = Mapper.getTech(techId);
            if (tech != null
                    && !player.hasTech(techId)
                    && !tech.isFactionTech()
                    && !tech.isUnitUpgrade()
                    && !techId.equals(state[4])
                    && tech.getFirstType().toString().equals(state[0])
                    && tech.getRequirements().orElse("").length() <= remaining
                    && (chosen == 1
                            ? tech.getRequirements().orElse("").length() == remaining
                            : game.getTechnologyDeck().stream().anyMatch(otherTechId -> {
                                TechnologyModel otherTech = Mapper.getTech(otherTechId);
                                return !otherTechId.equals(techId)
                                        && !otherTechId.equals(state[4])
                                        && otherTech != null
                                        && !player.hasTech(otherTechId)
                                        && !otherTech.isFactionTech()
                                        && !otherTech.isUnitUpgrade()
                                        && otherTech.getFirstType().toString().equals(state[0])
                                        && otherTech
                                                        .getRequirements()
                                                        .orElse("")
                                                        .length()
                                                == remaining
                                                        - tech.getRequirements()
                                                                .orElse("")
                                                                .length();
                            }))) {
                buttons.add(
                        Buttons.green(player.factionButtonChecker() + RESEARCH + techId, "Research " + tech.getName()));
            }
        }
        return buttons;
    }

    private static String getResearchMessage(Player player, String[] state) {
        int remaining = state.length == 5 ? Integer.parseInt(state[1]) - Integer.parseInt(state[3]) : 0;
        return player.getRepresentationNoPing()
                + ", research " + (state.length == 5 && "0".equals(state[2]) ? "the first" : "the second")
                + " technology for _Dedicated Study_. The remaining prerequisite total is " + remaining + ".";
    }

    private static boolean hasResearchPair(
            Game game, Player player, String type, int totalRequirements, String... excludedTechIds) {
        List<String> excluded = List.of(excludedTechIds);
        List<TechnologyModel> candidates = game.getTechnologyDeck().stream()
                .filter(techId -> !excluded.contains(techId))
                .map(Mapper::getTech)
                .filter(tech -> tech != null
                        && !player.hasTech(tech.getAlias())
                        && !tech.isFactionTech()
                        && !tech.isUnitUpgrade()
                        && tech.getFirstType().toString().equals(type))
                .toList();
        return candidates.stream().anyMatch(first -> candidates.stream()
                .anyMatch(second -> first != second
                        && first.getRequirements().orElse("").length()
                                        + second.getRequirements().orElse("").length()
                                == totalRequirements));
    }
}
