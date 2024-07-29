package ti4.commands.cardspn;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlayPN extends PNCardsSubcommandData {
    public PlayPN() {
        super(Constants.PLAY_PN, "Play Promissory Note");
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between () or Name/Part of Name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Please select what Promissory Note to play");
            return;
        }

        String value = option.getAsString().toLowerCase();
        String pnID = null;
        int pnIndex;
        try {
            pnIndex = Integer.parseInt(value);
            for (Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
                if (pn.getValue().equals(pnIndex)) {
                    pnID = pn.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
                String pnName = Mapper.getPromissoryNote(pn.getKey()).getName();
                if (pnName != null) {
                    pnName = pnName.toLowerCase();
                    if (pnName.contains(value) || pn.getKey().contains(value)) {
                        if (foundSimilarName && !cardName.equals(pnName)) {
                            MessageHelper.sendMessageToEventChannel(event, "Multiple cards with similar name founds, please use ID");
                            return;
                        }
                        pnID = pn.getKey();
                        foundSimilarName = true;
                        cardName = pnName;
                    }
                }
            }
        }

        if (pnID == null) {
            MessageHelper.sendMessageToEventChannel(event, "No such Promissory Note ID found, please retry");
            return;
        }

        playPN(event, game, player, pnID);
    }

    private void playPN(GenericInteractionCreateEvent event, Game game, Player player, String pnID) {
        ButtonHelper.resolvePNPlay(pnID, player, game, event);
    }
}
