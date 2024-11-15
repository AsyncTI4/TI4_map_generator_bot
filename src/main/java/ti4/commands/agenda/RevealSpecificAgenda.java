package ti4.commands.agenda;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.SecretObjectiveModel;

class RevealSpecificAgenda extends GameStateSubcommand {

    public RevealSpecificAgenda() {
        super(Constants.REVEAL_SPECIFIC, "Reveal top Agenda from deck", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.AGENDA_ID, "Agenda Card ID (text ID found in /search agendas)").setRequired(true).setAutoComplete(true));
        addOption(OptionType.BOOLEAN, Constants.FORCE, "Force reveal the agenda (even if it's not in the deck)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String agendaID = event.getOption(Constants.AGENDA_ID, "", OptionMapping::getAsString);
        if (!Mapper.isValidAgenda(agendaID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Agenda ID found, please retry");
            return;
        }

        Game game = getGame();
        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!game.revealAgenda(agendaID, force)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda not found in deck, please retry");
            return;
        }

        revealAgenda(game, event.getChannel(), agendaID);
    }

    /**
     * @deprecated This needs to be refactored to use {@link AgendaHelper#revealAgenda}'s version
     */
    @Deprecated
    public void revealAgenda(Game game, MessageChannel channel, String agendaID) {
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(agendaID);
        if (uniqueID == null) {
            MessageHelper.sendMessageToChannel(channel, "Agenda `" + agendaID + "` not found in discard, please retry");
            return;
        }

        game.setPhaseOfGame("agendawaiting");
        AgendaModel agendaDetails = Mapper.getAgenda(agendaID);
        String agendaTarget = agendaDetails.getTarget();
        String agendaType = agendaDetails.getType();
        String agendaName = agendaDetails.getName();
        boolean cov = false;

        //EMERGENCY SESSION
        if ("Emergency Session".equalsIgnoreCase(agendaName)) {
            MessageHelper.sendMessageToChannel(channel, "# " + game.getPing()
                + " Emergency Session revealed. This agenda phase will have an additional agenda compared to normal. Flipping next agenda");
            agendaID = game.revealAgenda(false);
            revealAgenda(game, channel, agendaID);
            return;
        }

        //ELECT LAW BUT NO LAWS IN PLAY
        if (agendaTarget.toLowerCase().contains("elect law") && game.getLaws().isEmpty()) {
            MessageHelper.sendMessageToChannel(channel,
                game.getPing() + "An \"Elect Law\" Agenda (" + agendaName + ") was revealed when no laws in play, flipping next agenda");
            agendaID = game.revealAgenda(false);
            revealAgenda(game, channel, agendaID);
            return;
        }

        if (agendaName != null && !"Covert Legislation".equalsIgnoreCase(agendaName)) {
            game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_" + uniqueID + "_" + agendaID);
        } else {
            boolean notEmergency = false;
            while (!notEmergency) {

                if ("Emergency Session".equalsIgnoreCase(agendaName)) {
                    game.revealAgenda(false);
                    MessageHelper.sendMessageToChannel(channel, game.getPing() + " Emergency Session revealed underneath Covert Legislation, discarding it.");
                }
                if (agendaTarget.toLowerCase().contains("elect law") && game.getLaws().isEmpty()) {
                    game.revealAgenda(false);
                    MessageHelper.sendMessageToChannel(channel,
                        game.getPing() + " an elect law agenda revealed underneath Covert Legislation while there were no laws in play, discarding it.");
                }
                String id2 = game.getNextAgenda(false);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName = agendaDetails.getName();
                game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_CL_covert");
                notEmergency = !"Emergency Session".equalsIgnoreCase(agendaName);
                if (agendaTarget.toLowerCase().contains("elect law") && game.getLaws().isEmpty()) {
                    notEmergency = false;
                }
                if (notEmergency) {
                    cov = true;

                    Player speaker = null;
                    if (game.getPlayer(game.getSpeakerUserID()) != null) {
                        speaker = game.getPlayers().get(game.getSpeakerUserID());
                    }

                    if (speaker != null) {
                        Map.Entry<String, Integer> entry = game.drawAgenda();
                        if (entry != null) {
                            String sb = "-----------\n" +
                                    "Game: " + game.getName() + "\n" +
                                    speaker.getRepresentationUnfogged() + "\n" +
                                    "Drawn Agendas:\n" +
                                    1 + ". " + Helper.getAgendaRepresentation(entry.getKey(), entry.getValue()) +
                                    "\n";
                            MessageHelper.sendMessageToChannel(speaker.getCardsInfoThread(), sb);
                        }
                    }
                }
            }
        }

        game.resetCurrentAgendaVotes();
        game.setHasHackElectionBeenPlayed(false);
        game.setPlayersWhoHitPersistentNoAfter("");
        game.setPlayersWhoHitPersistentNoWhen("");
        game.setLatestOutcomeVotedFor("");
        for (Player p2 : game.getRealPlayers()) {
            game.setStoredValue("latestOutcomeVotedFor" + p2.getFaction(), "");
            game.setStoredValue("preVoting" + p2.getFaction(), "");
        }
        game.setLatestWhenMsg("");
        game.setLatestAfterMsg("");
        MessageHelper.sendMessageToChannel(channel, Helper.getAgendaRepresentation(agendaID, uniqueID));
        String text = game.getPing()
            + " Please indicate whether you abstain from playing whens/afters below. If you have an action card with those windows, you may simply play it.";

        Date newTime = new Date();
        game.setLastActivePlayerPing(newTime);
        List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
        List<Button> afterButtons = AgendaHelper.getAfterButtons(game);

        MessageHelper.sendMessageToChannel(channel, text);

        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Whens", game, whenButtons, "when");
        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Afters", game, afterButtons, "after");

        ListVoteCount.turnOrder(game, channel);
        Button proceed = Buttons.red("proceedToVoting", "Skip waiting and start the voting for everyone");
        List<Button> proceedButtons = new ArrayList<>(List.of(proceed));
        Button transaction = Buttons.blue("transaction", "Transaction");
        proceedButtons.add(transaction);
        proceedButtons.add(Buttons.red("eraseMyVote", "Erase my vote & have me vote again"));
        proceedButtons.add(Buttons.red("eraseMyRiders", "Erase my riders"));
        MessageHelper.sendMessageToChannelWithButtons(channel, "Press this button if the last person forgot to react, but verbally said no whens/afters", proceedButtons);
        if (cov) {
            MessageHelper.sendMessageToChannel(channel,
                "# " + game.getPing() + " the agenda target is " + agendaTarget + ". Sent the agenda to the speakers cards info");
        }
        for (Player player : game.getRealPlayers()) {
            if (game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander") && !ButtonHelperCommanders.resolveFlorzenCommander(player, game).isEmpty()) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged() + " you have Florzen commander and may thus explore and ready a planet.",
                    ButtonHelperCommanders.resolveFlorzenCommander(player, game));
            }
        }
        if (game.getCurrentAgendaInfo().contains("Secret")) {
            StringBuilder summary = new StringBuilder("## Scored Secret Objectives:\n");
            for (Player p2 : game.getRealPlayers()) {
                for (String soID : p2.getSecretsScored().keySet()) {
                    SecretObjectiveModel so = Mapper.getSecretObjective(soID);
                    if (so != null) {
                        summary.append("- ").append(Emojis.SecretObjective).append("__**").append(so.getName()).append("**__: ").append(so.getText()).append("\n");
                    }
                }
            }
            MessageHelper.sendMessageToChannel(channel, summary.toString());
        }
    }
}
