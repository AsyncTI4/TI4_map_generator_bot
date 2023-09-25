package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.util.Map;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
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
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;

public class PlayAC extends ACCardsSubcommandData {
    public PlayAC() {
        super(Constants.PLAY_AC, "Play an Action Card");
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION_CARD_ID, "Action Card ID that is sent between () or Name/Part of Name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            sendMessage("Please select what Action Card to discard");
            return;
        }

        String reply = playAC(event, activeGame, player, option.getAsString().toLowerCase(), event.getChannel(), event.getGuild());
        if (reply != null) {
            sendMessage(reply);
        }
    }

    public static String playAC(GenericInteractionCreateEvent event, Game activeGame, Player player, String value, MessageChannel channel, Guild guild) {
        MessageChannel mainGameChannel = activeGame.getMainGameChannel() == null ? channel : activeGame.getMainGameChannel();

        String acID = null;
        int acIndex = -1;
        try {
            acIndex = Integer.parseInt(value);
            for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                if (so.getValue().equals(acIndex)) {
                    acID = so.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
                String actionCardName = Mapper.getActionCardName(ac.getKey());
                if (actionCardName != null) {
                    actionCardName = actionCardName.toLowerCase();
                    if (actionCardName.contains(value)) {
                        if (foundSimilarName && !cardName.equals(actionCardName)) {
                            return "Multiple cards with similar name founds, please use ID";
                        }
                        acID = ac.getKey();
                        acIndex = ac.getValue();
                        foundSimilarName = true;
                        cardName = actionCardName;
                    }
                }
            }
        }
        if (acID == null) {
            //sendMessage();
            return "No such Action Card ID found, please retry";
        }
        ActionCardModel actionCard = Mapper.getActionCard(acID);
        String actionCardTitle = actionCard.getName();
        String actionCardWindow = actionCard.getWindow();

        String activePlayerID = activeGame.getActivePlayer();
        if (player.isPassed() && activePlayerID != null) {
            Player activePlayer = activeGame.getPlayer(activePlayerID);
            if (activePlayer != null && activePlayer.hasTech("tp")) {
                return "You are passed and the active player has researched Transparasteel Plating. AC Play command cancelled.";
            }
        }

        activeGame.discardActionCard(player.getUserID(), acIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(guild, activeGame)).append(" ").append(activeGame.getName()).append("\n");

        if (activeGame.isFoWMode()) {
            sb.append("Someone played an Action Card:\n");
        } else {
            sb.append(Helper.getPlayerRepresentation(player, activeGame)).append(" played an Action Card:\n");
        }
        sb.append(actionCard.getRepresentation());
        List<Button> buttons = new ArrayList<>();
        Button sabotageButton = Button.danger("sabotage_ac_" + actionCardTitle, "Cancel AC With Sabotage").withEmoji(Emoji.fromFormatted(Emojis.Sabotage));
        buttons.add(sabotageButton);
        Player empy = Helper.getPlayerFromUnit(activeGame, "empyrean_mech");
        if (empy != null && ButtonHelperFactionSpecific.isNextToEmpyMechs(activeGame, player, empy) && !activeGame.getLaws().containsKey("articles_war")) {
            Button empyButton = Button.secondary("sabotage_empy_" + actionCardTitle, "Cancel " + actionCardTitle + " With Empyrean Mech ")
                .withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
            List<Button> empyButtons = new ArrayList<>();
            empyButtons.add(empyButton);
            Button refuse = Button.danger("deleteButtons", "Delete These Buttons");
            empyButtons.add(refuse);
            MessageHelper.sendMessageToChannelWithButtons(empy.getCardsInfoThread(activeGame),
                Helper.getPlayerRepresentation(empy, activeGame, activeGame.getGuild(), true) + "You have mechs adjacent to the player who played the AC. Use Buttons to decide whether to cancel.",
                empyButtons);

        }
        String instinctTrainingID = "it";
        for (Player player2 : activeGame.getPlayers().values()) {
            if (!player.equals(player2) && player2.hasTechReady(instinctTrainingID) && player2.getStrategicCC() > 0) {
                Button instinctButton = Button.secondary("sabotage_xxcha_" + actionCardTitle, "Cancel " + actionCardTitle + " With Instinct Training")
                    .withEmoji(Emoji.fromFormatted(Emojis.Xxcha));
                List<Button> xxchaButtons = new ArrayList<>();
                xxchaButtons.add(instinctButton);
                Button refuse = Button.danger("deleteButtons", "Delete These Buttons");
                xxchaButtons.add(refuse);
                MessageHelper.sendMessageToChannelWithButtons(player2.getCardsInfoThread(activeGame), Helper.getPlayerRepresentation(player2, activeGame, activeGame.getGuild(), true)
                    + "You have Instinct Training unexhausted and a cc available. Use Buttons to decide whether to cancel", xxchaButtons);
            }

        }

        Button noSabotageButton = Button.primary("no_sabotage", "No Sabotage").withEmoji(Emoji.fromFormatted(Emojis.NoSabotage));
        buttons.add(noSabotageButton);
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannel(mainGameChannel, sb.toString());
        } else {
            if (Helper.isSaboAllowed(activeGame, player)) {
                MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, sb.toString(), activeGame, player, buttons);
            } else {
                MessageHelper.sendMessageToChannel(mainGameChannel, sb.toString());
                MessageHelper.sendMessageToChannel(mainGameChannel,
                    "Either all sabos were in the discard or active player had Transparasteel Plating and everyone was passed. Instinct training and watcher mechs may still be viable, who knows. ");
            }
            if (actionCardTitle.contains("Manipulate Investments")) {
                MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeGame);
                List<Button> scButtons = new ArrayList<>();
                for (int sc = 1; sc < 9; sc++) {
                    Emoji scEmoji = Emoji.fromFormatted(Helper.getSCBackEmojiFromInteger(sc));
                    Button button;
                    if (scEmoji.getName().contains("SC") && scEmoji.getName().contains("Back")) {
                        button = Button.secondary("FFCC_" + player.getFaction() + "_increaseTGonSC_" + sc, " ").withEmoji(scEmoji);
                    } else {
                        button = Button.secondary("FFCC_" + player.getFaction() + "_increaseTGonSC_" + sc, sc + " " + Helper.getSCName(sc, activeGame));
                    }
                    scButtons.add(button);
                }
                scButtons.add(Button.danger("deleteButtons", "Done adding TG"));
                MessageHelper.sendMessageToChannelWithButtons(channel2, Helper.getPlayerRepresentation(player, activeGame, guild, false) + " Use buttons to increase tgs on SCs. Each press adds 1tg.",
                    scButtons);
            }
            if (actionCardTitle.contains("Archaeological Expedition")) {
                MessageChannel channel2 = ButtonHelper.getCorrectChannel(player, activeGame);
                List<Button> scButtons = ButtonHelper.getArcExpButtons(activeGame, player);
                MessageHelper.sendMessageToChannelWithButtons(channel2,
                    Helper.getPlayerRepresentation(player, activeGame, guild, false) + " After checking for sabos, use buttons to explore a planet type x 3 and gain any frags", scButtons);
            }

            if (actionCardWindow.contains("After an agenda is revealed")) {

                List<Button> afterButtons = AgendaHelper.getAfterButtons(activeGame);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", activeGame, afterButtons, "after");
                Date newTime = new Date();
                activeGame.setLastActivePlayerPing(newTime);

                String finChecker = "FFCC_" + player.getFaction() + "_";
                if (actionCardTitle.contains("Rider") || actionCardTitle.contains("Sanction")) {
                    List<Button> riderButtons = AgendaHelper.getAgendaButtons(actionCardTitle, activeGame, finChecker);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your rider target", activeGame, player, riderButtons);
                }
                if (actionCardTitle.contains("Hack Election")) {
                    activeGame.setHackElectionStatus(true);
                    Button setHack = Button.danger("hack_election", "Set the voting order as normal");
                    List<Button> hackButtons = List.of(setHack);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Voting order reversed. Please hit this button if hack election is sabod", activeGame, player, hackButtons);
                }

            }
            if (actionCardWindow.contains("When an agenda is revealed") && !actionCardTitle.contains("Veto")) {
                Date newTime = new Date();
                activeGame.setLastActivePlayerPing(newTime);
                List<Button> whenButtons = AgendaHelper.getWhenButtons(activeGame);
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no whens again.", activeGame, whenButtons, "when");
            }
            if ("Action".equalsIgnoreCase(actionCardWindow)) {
                String message = "Use buttons to end turn or do another action.";
                List<Button> systemButtons = ButtonHelper.getStartOfTurnButtons(player, activeGame, true, event);
                MessageChannel channel2 = activeGame.getMainGameChannel();
                if (activeGame.isFoWMode()) {
                    channel2 = player.getPrivateChannel();
                }
                MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
            }
        }

        //Fog of war ping
        if (activeGame.isFoWMode()) {
            String fowMessage = Helper.getPlayerRepresentation(player, activeGame) + " played an Action Card: " + actionCardTitle;
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, fowMessage);
            MessageHelper.sendPrivateMessageToPlayer(player, activeGame, "Played action card: " + actionCardTitle);
        }

        ACInfo.sendActionCardInfo(activeGame, player);
        return null;
    }
}
