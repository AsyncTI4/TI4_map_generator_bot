package ti4.commands.agenda;

import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

public class RevealSpecificAgenda extends AgendaSubcommandData {
    public RevealSpecificAgenda() {
        super(Constants.REVEAL_SPECIFIC, "Reveal top Agenda from deck");
        addOptions(new OptionData(OptionType.STRING, Constants.AGENDA_ID, "Agenda Card ID (text ID found in /search agendas)").setRequired(true).setAutoComplete(true));
        addOption(OptionType.BOOLEAN, Constants.FORCE, "Force reveal the agenda (even if it's not in the deck)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        String agendaID = event.getOption(Constants.AGENDA_ID, "", OptionMapping::getAsString);
        if (!Mapper.isValidAgenda(agendaID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Agenda ID found, please retry");
            return;
        }

        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!activeGame.revealAgenda(agendaID, force)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda not found in deck, please retry");
            return;
        }
        
        revealAgenda(event, activeGame, event.getChannel(), agendaID);
    }


    public void revealAgenda(GenericInteractionCreateEvent event, Game activeGame, MessageChannel channel, String agendaID) {
        LinkedHashMap<String, Integer> discardAgendas = activeGame.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(agendaID);
        if (uniqueID == null) {
            MessageHelper.sendMessageToChannel(channel, "Agenda `" + agendaID + "` not found in discard, please retry");
            return;
        }

        activeGame.setCurrentPhase("agendawaiting");
        AgendaModel agendaDetails = Mapper.getAgenda(agendaID);
        String agendaTarget = agendaDetails.getTarget();
        String agendaType = agendaDetails.getType();
        String agendaName = agendaDetails.getName();
        boolean cov = false;

        //EMERGENCY SESSION
        if ("Emergency Session".equalsIgnoreCase(agendaName)) {
            MessageHelper.sendMessageToChannel(channel, "# "+Helper.getGamePing(activeGame.getGuild(), activeGame)+" Emergency Session revealed. This agenda phase will have an additional agenda compared to normal. Flipping next agenda");
            agendaID = activeGame.revealAgenda(false);
            revealAgenda(event, activeGame, channel, agendaID);
            return;
        }

        //ELECT LAW BUT NO LAWS IN PLAY
        if (agendaTarget.contains("Law") && (activeGame.getLaws().isEmpty() || activeGame.getLaws().size() == 0)) {
            MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(activeGame.getGuild(), activeGame)+"An \"Elect Law\" Agenda ("+agendaName+") was revealed when no laws in play, flipping next agenda");
            agendaID = activeGame.revealAgenda(false);
            revealAgenda(event, activeGame, channel, agendaID);
            return;
        }

        if (agendaName != null && !"Covert Legislation".equalsIgnoreCase(agendaName)) {
            activeGame.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_" + uniqueID + "_" + agendaID);
        } else {
            boolean notEmergency = false;
            while(!notEmergency) {
                if ("Emergency Session".equalsIgnoreCase(agendaName)) {
                    activeGame.revealAgenda(false);
                    MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(activeGame.getGuild(), activeGame) + " Emergency Session revealed underneath Covert Legislation, discarding it.");
                }
                if (agendaTarget.toLowerCase().contains("elect law") && activeGame.getLaws().size() < 1) {
                    activeGame.revealAgenda(false);
                    MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(activeGame.getGuild(), activeGame) + " an elect law agenda revealed underneath Covert Legislation while there were no laws in play, discarding it.");
                }
                String id2 = activeGame.getNextAgenda(false);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName = agendaDetails.getName();
                activeGame.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_CL_covert");
                notEmergency = !"Emergency Session".equalsIgnoreCase(agendaName);
                if (agendaTarget.toLowerCase().contains("elect law") && activeGame.getLaws().size() < 1) {
                    notEmergency = false;
                }
                if (notEmergency) {
                    cov = true;

                    Player speaker = null;
                    if (activeGame.getPlayer(activeGame.getSpeaker()) != null) {
                        speaker = activeGame.getPlayers().get(activeGame.getSpeaker());
                    }

                    if (speaker != null) {
                        StringBuilder sb = new StringBuilder();
                        Map.Entry<String, Integer> entry = activeGame.drawAgenda();
                        sb.append("-----------\n");
                        sb.append("Game: ").append(activeGame.getName()).append("\n");
                        sb.append(ButtonHelper.getTrueIdentity(speaker, activeGame)).append("\n");
                        sb.append("Drawn Agendas:\n");
                        sb.append(1).append(". ").append(Helper.getAgendaRepresentation(entry.getKey(), entry.getValue()));
                        sb.append("\n");
                        MessageHelper.sendMessageToChannel(speaker.getCardsInfoThread(activeGame), sb.toString());
                    }
                }
            }
        }

        activeGame.resetCurrentAgendaVotes();
        activeGame.setHackElectionStatus(false);
        activeGame.setPlayersWhoHitPersistentNoAfter("");
        activeGame.setPlayersWhoHitPersistentNoWhen("");
        activeGame.setLatestOutcomeVotedFor("");
        activeGame.setLatestWhenMsg("");
        activeGame.setLatestAfterMsg("");
        MessageHelper.sendMessageToChannel(channel, Helper.getAgendaRepresentation(agendaID, uniqueID));
        String text = Helper.getGamePing(event, activeGame) + " Please indicate whether you abstain from playing whens/afters below. If you have an action card with those windows, you can simply play it.";

        
        Date newTime = new Date();
        activeGame.setLastActivePlayerPing(newTime);
        List<Button> whenButtons = AgendaHelper.getWhenButtons(activeGame);
        List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);

        MessageHelper.sendMessageToChannel(channel, text);

        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Whens", activeGame, whenButtons, "when");
        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Afters", activeGame, afterButtons,"after");

        ListVoteCount.turnOrder(event, activeGame, channel);
        Button proceed = Button.danger( "proceedToVoting", "Skip waiting and start the voting for everyone");
        List<Button> proceedButtons = new ArrayList<>(List.of(proceed));
        Button transaction = Button.primary("transaction", "Transaction");
        proceedButtons.add(transaction);
        proceedButtons.add(Button.danger("eraseMyVote", "Erase my vote & have me vote again"));
        MessageHelper.sendMessageToChannelWithButtons(channel, "Press this button if the last person forgot to react, but verbally said no whens/afters", proceedButtons);
        if(cov){
            MessageHelper.sendMessageToChannel(channel, "# "+Helper.getGamePing(activeGame.getGuild(), activeGame)+" the agenda target is "+agendaTarget+". Sent the agenda to the speakers cards info");
        }
        for(Player player : activeGame.getRealPlayers()){
            if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "florzencommander") && ButtonHelperFactionSpecific.resolveFlorzenCommander(player, activeGame).size() > 0){
                MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), ButtonHelper.getTrueIdentity(player, activeGame) +" you have Florzen commander and can thus explore and ready a planet", ButtonHelperFactionSpecific.resolveFlorzenCommander(player, activeGame));
            }
        }
    }
}
