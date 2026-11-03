package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.tech.ListTechService;

public final class RevenantArcanumAgent extends AgentModule<Player> {

    static final String ID = "revenantarcanumagent";
    private static final String LEGACY_USE = "useRevenantArcanumAgent_";
    private static final String SELECT_RETURNED_TECH = "selectRevenantArcanumReturnTech_";

    public RevenantArcanumAgent() {
        super(ID, "Lothos Yvollus, the Revenant of the Arcanum");
    }

    public static void offerAfterPass(Game game, Player passedPlayer) {
        if (game == null || passedPlayer == null) {
            return;
        }
        for (Player agentOwner : game.getRealPlayers()) {
            if (!agentOwner.hasUnexhaustedLeader(ID)
                    || returnTechButtons(game, passedPlayer).isEmpty()) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                    agentOwner.getCardsInfoThread(),
                    agentOwner.getRepresentationNoPing() + ", " + passedPlayer.getRepresentationNoPing()
                            + " passed. You may exhaust **Lothos Yvollus** to let them return a technology and research one with exactly 1 fewer prerequisite.",
                    offerButtons(agentOwner, passedPlayer));
        }
    }

    static List<Button> offerButtons(Player agentOwner, Player passedPlayer) {
        return List.of(
                Buttons.green(
                        ownedButtonId(agentOwner, ID, passedPlayer.getFaction()),
                        "Use Revenant Arcanum Agent",
                        FactionEmojis.revenant),
                Buttons.red(agentOwner.factionButtonChecker() + "deleteButtons", "No Thanks"));
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_USE)
    static void legacyUse(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_USE.length()));
    }

    @ButtonHandler(SELECT_RETURNED_TECH)
    static void selectReturnedTech(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String techId = buttonID.substring(SELECT_RETURNED_TECH.length());
        TechnologyModel returnedTech = Mapper.getTech(techId);
        if (returnedTech == null
                || !target.hasTech(techId)
                || returnedTech.isFactionTech()
                || returnedTech.isUnitUpgrade()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        target.removeTech(techId);
        List<TechnologyModel> researchableTechs = researchableTechs(game, target, returnedTech);
        if (researchableTechs.isEmpty()) {
            target.addTech(techId);
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "No researchable technology has exactly 1 fewer prerequisite.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentation() + " returned " + returnedTech.getNameRepresentation()
                        + " to the technology deck. Research a technology with exactly 1 fewer prerequisite.",
                ListTechService.getTechButtons(new ArrayList<>(researchableTechs), target));
        ButtonHelper.deleteMessage(event);
    }

    static List<Button> returnTechButtons(Game game, Player target) {
        return target.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .filter(tech -> !tech.isFactionTech() && !tech.isUnitUpgrade())
                .filter(tech -> !researchableTechs(game, target, tech).isEmpty())
                .map(tech -> Buttons.green(
                        target.factionButtonChecker() + SELECT_RETURNED_TECH + tech.getAlias(),
                        "Return " + tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    private static List<TechnologyModel> researchableTechs(Game game, Player target, TechnologyModel returnedTech) {
        int prerequisites = returnedTech.getRequirements().orElse("").length() - 1;
        if (prerequisites < 0) {
            return List.of();
        }
        return Mapper.getTechs().values().stream()
                .filter(tech -> game.getTechnologyDeck().contains(tech.getAlias()))
                .filter(tech -> !target.hasTech(tech.getAlias()))
                .filter(tech -> !target.getPurgedTechs().contains(tech.getAlias()))
                .filter(tech -> tech.getFaction().isEmpty()
                        || tech.getFaction().get().isBlank()
                        || target.getNotResearchedFactionTechs().contains(tech.getAlias()))
                .filter(tech -> tech.getRequirements().orElse("").length() == prerequisites)
                .filter(tech -> ListTechService.isTechResearchable(tech, target))
                .toList();
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.player(game, payload);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player target) {
        if (returnTechButtons(game, target).isEmpty()) {
            return Optional.of("That player has no technology to return for one with exactly 1 fewer prerequisite");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        return AgentOutcome.of(Message.plain(
                        target,
                        target.getRepresentation()
                                + ", choose a non-faction, non-unit technology to return to its deck with **Lothos Yvollus**.",
                        returnTechButtons(use.game(), target)))
                .deletingPressedMessage();
    }
}
