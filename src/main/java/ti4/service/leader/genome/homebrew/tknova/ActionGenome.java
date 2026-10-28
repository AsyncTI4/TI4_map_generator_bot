package ti4.service.leader.genome.homebrew.tknova;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.edict.EdictPhaseHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.genome.Genome;

public final class ActionGenome extends Genome<Player> {

    static final String ID = "tknovaactionagent";
    static final String DRAWN_EDICTS_KEY = "edictPhaseDrawnEdicts";

    public ActionGenome() {
        super(ID, "Action Genome");
    }

    public static void offerWhileEdictsAreDrawn(Game game, Player tyrant, List<String> drawnEdicts) {
        game.setStoredValue(DRAWN_EDICTS_KEY, String.join(",", drawnEdicts));
        for (Player holder : game.getRealPlayers()) {
            if (!holder.hasUnexhaustedLeader(ID) || !canSeeTyrant(game, holder, tyrant)) {
                continue;
            }
            Button offer = Buttons.gray(
                    ownedButtonId(holder, ID, tyrant.getFaction()),
                    useLabel(holder, ID, "Action Genome"),
                    FactionEmojis.Keleres);
            MessageHelper.sendMessageToChannelWithButton(
                    holder.getCorrectChannel(),
                    holder.getRepresentationUnfogged() + ", you may exhaust "
                            + AgentLifecycle.agentNameForHolder(game, holder, ID)
                            + " to let the Tyrant draw 1 additional edict.",
                    offer);
        }
    }

    @Override
    public Optional<Player> decode(Game game, Player user, String payload) {
        return AgentTargets.playerOrUser(game, user, payload);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, Player drawer) {
        if (extraEdict(game).isEmpty()) {
            return Optional.of("There is no further edict to draw");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Game game = use.game();
        Player drawer = use.payload();
        String extraEdict = extraEdict(game).orElseThrow();
        AgendaModel edict = Mapper.getAgenda(extraEdict);
        Button resolve = Buttons.green(drawer.factionButtonChecker() + "resolveEdict_" + extraEdict, edict.getName());
        List<MessageEmbed> embeds = List.of(edict.getRepresentationEmbed());
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                drawer.getCorrectChannel(),
                drawer.getRepresentation() + ", you drew 1 additional edict with " + use.agentNameFor(drawer)
                        + ". You may choose it instead of one of the edicts above.",
                embeds,
                List.of(resolve));
        String drawerName =
                FoWHelper.identityOrColorIfCanSeeStats(game, drawer, use.user(), drawer.getRepresentationNoPing());
        return AgentOutcome.of(Message.plain(use.user(), "Sent the additional edict to " + drawerName + "."));
    }

    private static Optional<String> extraEdict(Game game) {
        List<String> alreadyDrawn =
                Arrays.asList(game.getStoredValue(DRAWN_EDICTS_KEY).split(","));
        return Optional.ofNullable(EdictPhaseHandler.getEdictDeck(game)).stream()
                .flatMap(List::stream)
                .filter(edict -> !alreadyDrawn.contains(edict))
                .findFirst();
    }

    private static boolean canSeeTyrant(Game game, Player holder, Player tyrant) {
        return !game.isFowMode() || FoWHelper.canSeeStatsOfPlayer(game, tyrant, holder);
    }
}
