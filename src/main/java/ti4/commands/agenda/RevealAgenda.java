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
import ti4.buttons.Buttons;
import ti4.generator.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.helpers.CryypterHelper;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.SecretObjectiveModel;

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
            && (System.currentTimeMillis() - Long.parseLong(game.getStoredValue("lastAgendaReactTime"))) < 10 * 60 * 10) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Sorry, the last agenda was flipped too recently, so the bot is stopping here to prevent a double flip. Do /agenda reveal if there's no button and this was a mistake.");
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

        CryypterHelper.checkEnvoyUnlocks(game);

        game.setStoredValue("AssassinatedReps", "");
        game.setStoredValue("riskedPredictive", "");
        game.setStoredValue("conspiratorsFaction", "");
        String agendaID = game.revealAgenda(revealFromBottom);
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(agendaID);
        // Button manualResolve = Buttons.red("autoresolve_manual", "Resolve it Manually");
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
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if ((agendaTarget.toLowerCase().contains("elect law") || agendaID.equalsIgnoreCase("constitution"))
            && game.getLaws().isEmpty()) {
            MessageHelper.sendMessageToChannel(channel,
                game.getPing() + "A Law Related Agenda (" + agendaName
                    + ") was revealed when no laws in play, flipping next agenda");
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
            revealAgenda(event, revealFromBottom, game, channel);
            return;
        }
        if ((agendaTarget.toLowerCase().contains("secret objective"))
            && game.getScoredSecrets() < 1) {
            MessageHelper.sendMessageToChannel(channel,
                game.getPing() + "An Elect Secret Agenda (" + agendaName
                    + ") was revealed when no scored secrets were in play, flipping next agenda");
            aCount -= 1;
            game.setStoredValue("agendaCount", aCount + "");
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
                String id2 = game.getNextAgenda(revealFromBottom);
                AgendaModel agendaDetails2 = Mapper.getAgenda(id2);
                agendaTarget = agendaDetails2.getTarget();
                agendaType = agendaDetails2.getType();
                agendaName = agendaModel.getName();
                game.setCurrentAgendaInfo(agendaType + "_" + agendaTarget + "_CL_covert");
                if ((agendaTarget.toLowerCase().contains("elect law") || id2.equalsIgnoreCase("constitution"))
                    && game.getLaws().isEmpty()) {
                    notEmergency = false;
                    game.revealAgenda(revealFromBottom);
                    MessageHelper.sendMessageToChannel(channel,
                        game.getPing()
                            + " an elect law agenda revealed underneath Covert Legislation while there were no laws in play, discarding it.");
                }
                if ((agendaTarget.toLowerCase().contains("secret objective"))
                    && game.getScoredSecrets() < 1) {
                    MessageHelper.sendMessageToChannel(channel,
                        game.getPing() + "An Elect Secret Agenda (" + agendaName
                            + ") was revealed under Covert when no scored secrets were in play, flipping next agenda");
                    notEmergency = false;
                    game.revealAgenda(revealFromBottom);
                }

                if (notEmergency) {
                    cov = true;

                    Player speaker = null;
                    if (game.getPlayer(game.getSpeakerUserID()) != null) {
                        speaker = game.getPlayers().get(game.getSpeakerUserID());
                    }
                    if (speaker != null) {
                        String sb = speaker.getRepresentationUnfogged() +
                            " this is the top agenda for Covert Legislation:";
                        List<MessageEmbed> embeds = List.of(Mapper.getAgenda(id2).getRepresentationEmbed());
                        MessageHelper.sendMessageEmbedsToCardsInfoThread(game, speaker, sb, embeds);
                        game.drawAgenda();

                    }
                }
            }
        }
        game.setStoredValue("Pass On Shenanigans", "");
        game.setStoredValue("Abstain On Agenda", "");
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
        if (!action) {
            AgendaHelper.offerEveryonePrepassOnShenanigans(game);
            AgendaHelper.offerEveryonePreAbstain(game);
            AgendaHelper.checkForAssigningGeneticRecombination(game);
            AgendaHelper.checkForPoliticalSecret(game);
        }

        MessageEmbed agendaEmbed = agendaModel.getRepresentationEmbed();
        String revealMessage = game.getPing() + "\nAn agenda has been revealed";
        MessageHelper.sendMessageToChannelWithEmbed(channel, revealMessage, agendaEmbed);
        if (!action) {
            MapGenerator.drawAgendaBanner(aCount, game);
            // MessageHelper.sendMessageToChannel(channel,"The game believes this is agenda #" + aCount + " of this agenda phase");

        }
        StringBuilder whensAftersMessage = new StringBuilder(
            "Please indicate whether you abstain from playing whens/afters below.\nIf you have an action card with those windows, you may simply play it.");
        if (action) {
            whensAftersMessage.append("\nYou may play afters during this agenda.");
        }
        game.setLastActivePlayerPing(new Date());
        List<Button> whenButtons = AgendaHelper.getWhenButtons(game);
        List<Button> afterButtons = AgendaHelper.getAfterButtons(game);

        MessageHelper.sendMessageToChannel(channel, whensAftersMessage.toString());
        if (!action) {
            MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Whens", game, whenButtons, "when");
        }
        MessageHelper.sendMessageToChannelWithPersistentReacts(channel, "Afters", game, afterButtons, "after");

        game.setStoredValue("lastAgendaReactTime", "" + System.currentTimeMillis());

        List<Button> proceedButtons = new ArrayList<>();
        String msg;

        if (action) {
            msg = "It seems likely you are resolving Midir, the Edyn hero, you may use this button to skip straight to the resolution.";
            proceedButtons.add(Buttons.red("autoresolve_manual", "Skip Straight To Resolution"));
        } else {
            ListVoteCount.turnOrder(game, channel);
            msg = "Press this button if the last person forgot to react, but verbally said no whens/afters";
            proceedButtons.add(Buttons.red("proceedToVoting", "Skip waiting and start the voting for everyone"));
            proceedButtons.add(Buttons.blue("transaction", "Transaction"));
            proceedButtons.add(Buttons.red("eraseMyVote", "Erase my vote & have me vote again"));
            proceedButtons.add(Buttons.red("eraseMyRiders", "Erase my riders"));
            proceedButtons.add(Buttons.gray("refreshAgenda", "Refresh Agenda"));
        }
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, proceedButtons);
        if (cov) {
            MessageHelper.sendMessageToChannel(channel,
                "# " + game.getPing() + " the agenda target is " + agendaTarget
                    + ". Sent the agenda to the speakers cards info");
        }

        if (!action && aCount == 1) {
            AgendaHelper.pingAboutDebt(game);
            String key = "round" + game.getRound() + "AgendaPlacement";
            if (!game.getStoredValue(key).isEmpty() && !game.isFowMode()) {
                StringBuilder message = new StringBuilder("Politics holder did the following with the agendas in terms of topping or bottoming them:");
                for (String actionA : game.getStoredValue(key).split("_")) {
                    message.append(" ").append(StringUtils.capitalize(actionA));
                }
                MessageHelper.sendMessageToChannel(channel,
                    message.toString());

            }
        }
        for (Player player : game.getRealPlayers()) {
            if (!action && game.playerHasLeaderUnlockedOrAlliance(player, "florzencommander")
                && !ButtonHelperCommanders.resolveFlorzenCommander(player, game).isEmpty() && aCount == 2) {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                        + " you have Quaxdol Junitas, the Florzen commander, and may thus explore and ready a planet.",
                    ButtonHelperCommanders.resolveFlorzenCommander(player, game));
            }
        }
        if (!game.isFowMode() && !action) {
            ButtonHelper.updateMap(game, event,
                "Start of the agenda " + agendaName + " (Agenda #" + aCount + ")");
            game.setStoredValue("startTimeOfRound" + game.getRound() + "Agenda" + aCount, System.currentTimeMillis() + "");
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
