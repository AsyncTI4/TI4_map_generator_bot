package ti4.commands.agenda;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

public class RevealAgenda extends AgendaSubcommandData {
    public RevealAgenda() {
        super(Constants.REVEAL, "Reveal top Agenda from deck");
        addOption(OptionType.BOOLEAN, Constants.REVEAL_FROM_BOTTOM,
            "Reveal the agenda from the bottom of the deck instead of the top");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        OptionMapping revealFromBottomOption = event.getOption(Constants.REVEAL_FROM_BOTTOM);
        boolean revealFromBottom = false;
        if (revealFromBottomOption != null) {
            revealFromBottom = revealFromBottomOption.getAsBoolean();
        }
        revealAgenda(event, revealFromBottom, activeGame, event.getChannel());
    }

    public static void revealAgenda(GenericInteractionCreateEvent event, boolean revealFromBottom, Game activeGame,
        MessageChannel channel) {
        if (!activeGame.getStoredValue("lastAgendaReactTime").isEmpty()
            && ((new Date().getTime())
                - Long.parseLong(activeGame.getStoredValue("lastAgendaReactTime"))) < 10 * 60
                    * 10) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Sorry, the last agenda was flipped too recently, so the bot is stopping here to prevent a double flip. Do /agenda reveal if theres no button and this was a mistake");
            return;
        }
        activeGame.setStoredValue("noWhenThisAgenda", "");
        activeGame.setStoredValue("noAfterThisAgenda", "");
        activeGame.setStoredValue("AssassinatedReps", "");
        activeGame.setStoredValue("riskedPredictive", "");
        activeGame.setStoredValue("conspiratorsFaction", "");
        String agendaID = activeGame.revealAgenda(revealFromBottom);
        Map<String, Integer> discardAgendas = activeGame.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(agendaID);
        // Button manualResolve = Button.danger("autoresolve_manual", "Resolve it
        // Manually");
        boolean action = false;
        if (!"action".equalsIgnoreCase(activeGame.getCurrentPhase())) {
            activeGame.setCurrentPhase("agendawaiting");
        } else {
            action = true;
        }

        AgendaModel agendaModel = Mapper.getAgenda(agendaID);
        String agendaTarget = agendaModel.getTarget();
        String agendaType = agendaModel.getType();
        String agendaName = agendaModel.getName();
        boolean cov = false;

        if ("Emergency Session".equalsIgnoreCase(agendaName)) {
            MessageHelper.sendMessageToChannel(channel, "# " + activeGame.getPing()
                + " __Emergency Session__ revealed.\n## This agenda phase will have an additional agenda compared to normal. Flipping next agenda");
            revealAgenda(event, revealFromBottom, activeGame, channel);
            return;
        }
        if ((agendaTarget.toLowerCase().contains("elect law") || agendaID.equalsIgnoreCase("constitution"))
            && activeGame.getLaws().size() < 1) {
            MessageHelper.sendMessageToChannel(channel,
                activeGame.getPing() + "A Law Related Agenda (" + agendaName
                    + ") was revealed when no laws in play, flipping next agenda");
            revealAgenda(event, revealFromBottom, activeGame, channel);
            return;
        }
        if (agendaName != null && !"Covert Legislation".equalsIgnoreCase(agendaName)) {
            activeGame.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_" + uniqueID + "_" + agendaID);
        } else {
            boolean notEmergency = false;
            while (!notEmergency) {
                if ("Emergency Session".equalsIgnoreCase(agendaName)) {
                    activeGame.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel, activeGame.getPing()
                        + " Emergency Session revealed underneath Covert Legislation, discarding it.");
                }
                notEmergency = !"Emergency Session".equalsIgnoreCase(agendaName);
                if ((agendaTarget.toLowerCase().contains("elect law") || agendaID.equalsIgnoreCase("constitution"))
                    && activeGame.getLaws().size() < 1) {
                    notEmergency = false;
                    activeGame.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel,
                        activeGame.getPing()
                            + " an elect law agenda revealed underneath Covert Legislation while there were no laws in play, discarding it.");
                }
                String id2 = activeGame.getNextAgenda(revealFromBottom);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName = agendaModel.getName();
                activeGame.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_CL_covert");

                if (notEmergency) {
                    cov = true;

                    Player speaker = null;
                    if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
                        speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
                    }
                    if (speaker != null) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(speaker.getRepresentation(true, true))
                            .append(" this is the top agenda for Covert Legislation:");
                        List<MessageEmbed> embeds = List.of(Mapper.getAgenda(id2).getRepresentationEmbed());
                        MessageHelper.sendMessageEmbedsToCardsInfoThread(activeGame, speaker, sb.toString(), embeds);
                        activeGame.drawAgenda();

                    }
                }
            }
        }
        activeGame.setStoredValue("Pass On Shenanigans", "");
        activeGame.setStoredValue("Abstain On Agenda", "");
        if (!action) {
            AgendaHelper.offerEveryonePrepassOnShenanigans(activeGame);
            AgendaHelper.offerEveryonePreAbstain(activeGame);
            AgendaHelper.checkForAssigningGeneticRecombination(activeGame);
            AgendaHelper.checkForPoliticalSecret(activeGame);
        }
        String agendaCount = activeGame.getStoredValue("agendaCount");
        int aCount = 0;
        if (agendaCount.isEmpty()) {
            aCount = 1;
        } else {
            aCount = Integer.parseInt(agendaCount) + 1;
        }
        activeGame.setStoredValue("agendaCount", aCount + "");
        activeGame.resetCurrentAgendaVotes();
        activeGame.setHackElectionStatus(false);
        activeGame.setPlayersWhoHitPersistentNoAfter("");
        activeGame.setPlayersWhoHitPersistentNoWhen("");
        activeGame.setLatestOutcomeVotedFor("");
        activeGame.setLatestWhenMsg("");
        activeGame.setLatestAfterMsg("");
        MessageEmbed agendaEmbed = agendaModel.getRepresentationEmbed();
        String revealMessage = activeGame.getPing() + "\nAn agenda has been revealed";
        MessageHelper.sendMessageToChannelWithEmbed(channel, revealMessage, agendaEmbed);

        StringBuilder whensAftersMessage = new StringBuilder(
            "Please indicate whether you abstain from playing whens/afters below.\nIf you have an action card with those windows, you can simply play it.");
        if (action) {
            whensAftersMessage.append("\nYou can play afters during this agenda");
        }
        activeGame.setLastActivePlayerPing(new Date());
        List<Button> whenButtons = AgendaHelper.getWhenButtons(activeGame);
        List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);

        MessageHelper.sendMessageToChannel(channel, whensAftersMessage.toString());
        if (!action) {
            MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Whens", activeGame, whenButtons, "when");
        }
        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Afters", activeGame, afterButtons, "after");

        activeGame.setStoredValue("lastAgendaReactTime", "" + new Date().getTime());

        List<Button> proceedButtons = new ArrayList<>();
        String msg;

        if (action) {
            msg = "It seems likely you are resolving Edyn hero, you can use this button to skip straight to the resolution";
            proceedButtons.add(Button.danger("autoresolve_manual", "Skip Straight To Resolution"));
        } else {
            ListVoteCount.turnOrder(event, activeGame, channel);
            msg = "Press this button if the last person forgot to react, but verbally said no whens/afters";
            proceedButtons.add(Button.danger("proceedToVoting", "Skip waiting and start the voting for everyone"));
            proceedButtons.add(Button.primary("transaction", "Transaction"));
            proceedButtons.add(Button.danger("eraseMyVote", "Erase my vote & have me vote again"));
            proceedButtons.add(Button.danger("eraseMyRiders", "Erase my riders"));
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, proceedButtons);
        if (cov) {
            MessageHelper.sendMessageToChannel(channel,
                "# " + activeGame.getPing() + " the agenda target is " + agendaTarget
                    + ". Sent the agenda to the speakers cards info");
        }
        MessageHelper.sendMessageToChannel(channel,
            "The game believes this is agenda #" + aCount + " of this agenda phase");
        if (!action && aCount == 1) {
            AgendaHelper.pingAboutDebt(activeGame);
        }
        for (Player player : activeGame.getRealPlayers()) {
            if (!action && activeGame.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && ButtonHelperCommanders.resolveFlorzenCommander(player, activeGame).size() > 0 && aCount == 2) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation(true, true)
                        + " you have Florzen commander and can thus explore and ready a planet",
                    ButtonHelperCommanders.resolveFlorzenCommander(player, activeGame));
            }
        }
        if (!activeGame.isFoWMode() && !action) {
            ButtonHelper.updateMap(activeGame, event,
                "Start of the agenda " + agendaName + " (Agenda #" + aCount + ")");
            activeGame.setStoredValue("startTimeOfRound" + activeGame.getRound() + "Agenda" + aCount, new Date().getTime() + "");
        }
    }
}
