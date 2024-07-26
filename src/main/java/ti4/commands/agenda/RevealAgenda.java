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
import software.amazon.awssdk.utils.StringUtils;
import ti4.generator.Mapper;
import ti4.generator.MapGenerator;
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
        Game game = getActiveGame();
        OptionMapping revealFromBottomOption = event.getOption(Constants.REVEAL_FROM_BOTTOM);
        boolean revealFromBottom = false;
        if (revealFromBottomOption != null) {
            revealFromBottom = revealFromBottomOption.getAsBoolean();
        }
        revealAgenda(event, revealFromBottom, game, event.getChannel());
    }

    public static void revealAgenda(GenericInteractionCreateEvent event, boolean revealFromBottom, Game game, MessageChannel channel) {
        if (game.getMainGameChannel() != null) {
            channel = game.getMainGameChannel();
        }
        if (!game.getStoredValue("lastAgendaReactTime").isEmpty()
            && ((new Date().getTime()) - Long.parseLong(game.getStoredValue("lastAgendaReactTime"))) < 10 * 60 * 10) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Sorry, the last agenda was flipped too recently, so the bot is stopping here to prevent a double flip. Do `/agenda reveal` if there's no button and this was a mistake.");
            return;
        }

        String agendaCount = game.getStoredValue("agendaCount");
        int aCount = 0;
        if (agendaCount.isEmpty()) {
            aCount = 1;
        } else {
            aCount = Integer.parseInt(agendaCount) + 1;
        }
        game.setStoredValue("agendaCount", aCount + "");
        if (aCount == 1 && game.isShowBanners()) {
            MapGenerator.drawPhaseBanner("agenda", game.getRound(), game.getActionsChannel());
        }

        game.setStoredValue("noWhenThisAgenda", "");
        game.setStoredValue("noAfterThisAgenda", "");
        game.setStoredValue("AssassinatedReps", "");
        game.setStoredValue("riskedPredictive", "");
        game.setStoredValue("conspiratorsFaction", "");
        String agendaID = game.revealAgenda(revealFromBottom);
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(agendaID);
        // Button manualResolve = Button.danger("autoresolve_manual", "Resolve it
        // Manually");
        boolean action = false;
        if (!"action".equalsIgnoreCase(game.getPhaseOfGame())) {
            game.setPhaseOfGame("agendawaiting");
        } else {
            action = true;
        }

        AgendaModel agendaModel = Mapper.getAgenda(agendaID);
        String agendaTarget = agendaModel.getTarget();
        String agendaType = agendaModel.getType();
        String agendaName = agendaModel.getName();
        boolean cov = false;

        if ("Emergency Session".equalsIgnoreCase(agendaName)) {
            MessageHelper.sendMessageToChannel(channel, "# " + game.getPing()
                + " __Emergency Session__ revealed.\n## This agenda phase will have an additional agenda compared to normal. Flipping next agenda");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if ((agendaTarget.toLowerCase().contains("elect law") || agendaID.equalsIgnoreCase("constitution"))
            && game.getLaws().size() < 1) {
            MessageHelper.sendMessageToChannel(channel,
                game.getPing() + "A Law Related Agenda (" + agendaName
                    + ") was revealed when no laws in play, flipping next agenda");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if (agendaName != null && !"Covert Legislation".equalsIgnoreCase(agendaName)) {
            game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_" + uniqueID + "_" + agendaID);
        } else {
            boolean notEmergency = false;
            while (!notEmergency) {
                if ("Emergency Session".equalsIgnoreCase(agendaName)) {
                    game.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel, game.getPing()
                        + " Emergency Session revealed underneath Covert Legislation, discarding it.");
                }
                notEmergency = !"Emergency Session".equalsIgnoreCase(agendaName);
                if ((agendaTarget.toLowerCase().contains("elect law") || agendaID.equalsIgnoreCase("constitution"))
                    && game.getLaws().size() < 1) {
                    notEmergency = false;
                    game.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel,
                        game.getPing()
                            + " an elect law agenda revealed underneath Covert Legislation while there were no laws in play, discarding it.");
                }
                String id2 = game.getNextAgenda(revealFromBottom);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName = agendaModel.getName();
                game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_CL_covert");

                if (notEmergency) {
                    cov = true;

                    Player speaker = null;
                    if (game.getPlayer(game.getSpeaker()) != null) {
                        speaker = game.getPlayers().get(game.getSpeaker());
                    }
                    if (speaker != null) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(speaker.getRepresentation(true, true))
                            .append(" this is the top agenda for Covert Legislation:");
                        List<MessageEmbed> embeds = List.of(Mapper.getAgenda(id2).getRepresentationEmbed());
                        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, speaker, sb.toString(), embeds);
                        game.drawAgenda();

                    }
                }
            }
        }
        game.setStoredValue("Pass On Shenanigans", "");
        game.setStoredValue("Abstain On Agenda", "");
        if (!action) {
            AgendaHelper.offerEveryonePrepassOnShenanigans(game);
            AgendaHelper.offerEveryonePreAbstain(game);
            AgendaHelper.checkForAssigningGeneticRecombination(game);
            AgendaHelper.checkForPoliticalSecret(game);
        }
        game.resetCurrentAgendaVotes();
        game.setHasHackElectionBeenPlayed(false);
        game.setPlayersWhoHitPersistentNoAfter("");
        game.setPlayersWhoHitPersistentNoWhen("");
        game.setLatestOutcomeVotedFor("");
        game.setLatestWhenMsg("");
        game.setLatestAfterMsg("");
        MessageEmbed agendaEmbed = agendaModel.getRepresentationEmbed();
        String revealMessage = game.getPing() + "\nAn agenda has been revealed";
        MessageHelper.sendMessageToChannelWithEmbed(channel, revealMessage, agendaEmbed);

        StringBuilder whensAftersMessage = new StringBuilder(
            "Please indicate whether you abstain from playing \"when\"s or \"after\"s below.\nIf you have an action card with those windows, you may simply play it.");
        if (action) {
            whensAftersMessage.append("\nYou may play \"after\"s during this agenda.");
        }
        game.setLastActivePlayerPing(new Date());
        List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
        List<Button> afterButtons = AgendaHelper.getAfterButtons(game);

        MessageHelper.sendMessageToChannel(channel, whensAftersMessage.toString());
        if (!action) {
            MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Whens", game, whenButtons, "when");
        }
        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Afters", game, afterButtons, "after");

        game.setStoredValue("lastAgendaReactTime", "" + new Date().getTime());

        List<Button> proceedButtons = new ArrayList<>();
        String msg;

        if (action) {
            msg = "It seems likely you are resolving Midir, the Edyn hero, you may use this button to skip straight to the resolution.";
            proceedButtons.add(Button.danger("autoresolve_manual", "Skip Straight To Resolution"));
        } else {
            ListVoteCount.turnOrder(event, game, channel);
            msg = "Press this button if the last player forgot to react, but verbally said \"No Whens/Afters\".";
            proceedButtons.add(Button.danger("proceedToVoting", "Skip Waiting And Start The Voting For Everyone"));
            proceedButtons.add(Button.primary("transaction", "Transaction"));
            proceedButtons.add(Button.danger("eraseMyVote", "Erase M Vote And Have Me Vote Again"));
            proceedButtons.add(Button.danger("eraseMyRiders", "Erase My Riders"));
            proceedButtons.add(Button.secondary("refreshAgenda", "Refresh Agenda"));
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, proceedButtons);
        if (cov) {
            MessageHelper.sendMessageToChannel(channel,
                "# " + game.getPing() + " the agenda target is " + agendaTarget
                    + ". Sent the agenda to the speaker's `#Cards Info` thread.");
        }
        MessageHelper.sendMessageToChannel(channel,
            "The game believes this is agenda #" + aCount + " of this agenda phase");
        if (!action && aCount == 1) {
            AgendaHelper.pingAboutDebt(game);
            String key = "round" + game.getRound() + "AgendaPlacement";
            if (!game.getStoredValue(key).isEmpty() && !game.isFowMode()) {
                String message = "Politics holder did the following with the agendas in terms of topping or bottoming them:";
                for (String actionA : game.getStoredValue(key).split("_")) {
                    message = message + " " + StringUtils.capitalize(actionA);
                }
                MessageHelper.sendMessageToChannel(channel,
                    message);

            }
        }
        for (Player player : game.getRealPlayers()) {
            if (!action && game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && ButtonHelperCommanders.resolveFlorzenCommander(player, game).size() > 0 && aCount == 2) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentation(true, true)
                        + " you have Quaxdol Junitas, the Florzen commander, and may thus explore and ready a planet.",
                    ButtonHelperCommanders.resolveFlorzenCommander(player, game));
            }
        }
        if (!game.isFowMode() && !action) {
            ButtonHelper.updateMap(game, event,
                "Start of the agenda " + agendaName + " (Agenda #" + aCount + ")");
            game.setStoredValue("startTimeOfRound" + game.getRound() + "Agenda" + aCount, new Date().getTime() + "");
        }
    }
}
