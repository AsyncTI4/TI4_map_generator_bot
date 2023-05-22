package ti4.commands.cardsac;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.ButtonListener;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
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
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            sendMessage("Please select what Action Card to discard");
            return;
        }

        String reply = playAC(event, activeMap, player, option.getAsString().toLowerCase(), event.getChannel(), event.getGuild());
        if (reply != null) {
            sendMessage(reply);
        }
    }

    public static String playAC(GenericInteractionCreateEvent event, Map activeMap, Player player, String value, MessageChannel channel, Guild guild) {
        MessageChannel mainGameChannel = activeMap.getMainGameChannel() == null ? channel : activeMap.getMainGameChannel();

        String acID = null;
        int acIndex = -1;
        try {
            acIndex = Integer.parseInt(value);
            for (java.util.Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                if (so.getValue().equals(acIndex)) {
                    acID = so.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (java.util.Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
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
        String actionCardTitle = actionCard.name;
        String actionCardPhase = actionCard.phase;
        String actionCardWindow = actionCard.window;
        String actionCardText = actionCard.text;

        String activePlayerID = activeMap.getActivePlayer();
        if (player.isPassed() && activePlayerID != null) {
            Player activePlayer = activeMap.getPlayer(activePlayerID);
            if (activePlayer != null && activePlayer.getTechs().contains("tp")) {
                return "You are passed and the active player has researched Transparasteel Plating. AC Play command cancelled.";
            }
        }

        activeMap.discardActionCard(player.getUserID(), acIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(guild, activeMap)).append(" ").append(activeMap.getName()).append("\n");

        if (activeMap.isFoWMode()) {
            sb.append("Someone played an Action Card:\n");
        } else {
            sb.append(Helper.getPlayerRepresentation(player, activeMap)).append(" played an Action Card:\n");
        }
        sb.append(actionCard.getRepresentation());
        List<Button> buttons = new ArrayList<Button>();
        Button sabotageButton = Button.danger("sabotage_ac", "Cancel AC With Sabotage").withEmoji(Emoji.fromFormatted(Emojis.Sabotage));
        buttons.add(sabotageButton);
        if (activeMap.isEmpyInTheGame() || activeMap.isFoWMode()) {
            Button empyButton = Button.secondary("sabotage_empy", "Cancel AC With Empyrean Mech ").withEmoji(Emoji.fromFormatted(Helper.getEmojiFromDiscord("mech")));
            buttons.add(empyButton);
        }
        if (activeMap.doesAnyoneHaveInstinctTraining() || activeMap.isFoWMode()) {
            Button instinctButton = Button.secondary("sabotage_xxcha", "Cancel AC With Instinct Training").withEmoji(Emoji.fromFormatted(Helper.getFactionIconFromDiscord("Xxcha")));
            buttons.add(instinctButton);
        }
        Button noSabotageButton = Button.primary("no_sabotage", "No Sabotage").withEmoji(Emoji.fromFormatted(Emojis.NoSabotage));
        buttons.add(noSabotageButton);
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannel(mainGameChannel, sb.toString());
        } else {
            MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, sb.toString(), activeMap, player, buttons);

            if (actionCardWindow.contains("After an agenda is revealed")) {
                Button playAfter = Button.danger("play_after", "Play A Non-AC Rider");
                Button noAfter = Button.primary("no_after", "No Afters").withEmoji(Emoji.fromFormatted(Emojis.noafters));
                Button noAfterPersistent = Button.primary("no_after_persistent", "No Afters No Matter What (for this agenda)").withEmoji(Emoji.fromFormatted(Emojis.noafters));
                List<Button> afterButtons = new ArrayList<>(List.of(playAfter, noAfter, noAfterPersistent));
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no afters again.", activeMap, afterButtons, "after");


                if (actionCardTitle.contains("Rider") || actionCardTitle.contains("Sanction") ) {
                    List<Button> riderButtons = AgendaHelper.getRiderButtons(actionCardTitle, activeMap);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please select your rider target", activeMap, player, riderButtons);
                }
                if (actionCardTitle.contains("Hack Election") ) {
                    Button setHack = Button.danger("hack_election", "Set the voting order as reversed");
                    List<Button> hackButtons =  List.of(setHack);
                    MessageHelper.sendMessageToChannelWithFactionReact(mainGameChannel, "Please hit this button after confirming no sabo on the hack election", activeMap, player, hackButtons);
                }

            }
            if (actionCardWindow.contains("When an agenda is revealed") && !actionCardTitle.contains("Veto")) {
                Button playWhen = Button.danger("play_when", "Play When");
                Button noWhen = Button.primary("no_when", "No Whens").withEmoji(Emoji.fromFormatted(Emojis.nowhens));
                Button noWhenPersistent = Button.primary("no_when_persistent", "No Whens No Matter What (for this agenda)").withEmoji(Emoji.fromFormatted(Emojis.nowhens));

                List<Button> whenButtons = new ArrayList<>(List.of(playWhen, noWhen, noWhenPersistent));
                MessageHelper.sendMessageToChannelWithPersistentReacts(mainGameChannel, "Please indicate no whens again.", activeMap, whenButtons, "when");




            }
        }

        //Fog of war ping
		if (activeMap.isFoWMode()) {
            String fowMessage = Helper.getPlayerRepresentation(player, activeMap) + " played an Action Card: " + actionCardTitle;
			FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, fowMessage);
            MessageHelper.sendPrivateMessageToPlayer(player, activeMap, "Played action card: " + actionCardTitle);
		}

        ACInfo.sendActionCardInfo(activeMap, player);
        return null;
    }
}
