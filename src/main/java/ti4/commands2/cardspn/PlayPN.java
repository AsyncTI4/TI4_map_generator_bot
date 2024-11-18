package ti4.commands2.cardspn;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

class PlayPN extends GameStateSubcommand {

    public PlayPN() {
        super(Constants.PLAY_PN, "Play Promissory Note", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between () or Name/Part of Name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();

        String value = event.getOption(Constants.PROMISSORY_NOTE_ID).getAsString().toLowerCase();
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

        playPN(event, getGame(), player, pnID);
    }

    private void playPN(GenericInteractionCreateEvent event, Game game, Player player, String pnID) {
        PromissoryNoteHelper.resolvePNPlay(pnID, player, game, event);
    }

    @ButtonHandler("resolvePNPlay_")
    public static void resolvePNPlay(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pnID = buttonID.replace("resolvePNPlay_", "");

        if (pnID.contains("ra_")) {
            String tech = AliasHandler.resolveTech(pnID.replace("ra_", ""));
            TechnologyModel techModel = Mapper.getTech(tech);
            pnID = pnID.replace("_" + tech, "");
            String message = player.getFactionEmojiOrColor() + " Acquired The Tech " + techModel.getRepresentation(false) + " via Research Agreement";
            player.addTech(tech);
            String key = "RAForRound" + game.getRound() + player.getFaction();
            if (game.getStoredValue(key).isEmpty()) {
                game.setStoredValue(key, tech);
            } else {
                game.setStoredValue(key, game.getStoredValue(key) + "." + tech);
            }
            ButtonHelperCommanders.resolveNekroCommanderCheck(player, tech, game);
            CommanderUnlockCheck.checkPlayer(player, "jolnar", "nekro", "mirveda", "dihmohn");
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
        PromissoryNoteHelper.resolvePNPlay(pnID, player, game, event);
        if (!"bmfNotHand".equalsIgnoreCase(pnID)) {
            ButtonHelper.deleteMessage(event);
        }

        var possibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.PROMISSORY_NOTES, pnID, player.getNumberTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Combat modifier will be applied next time you push the combat roll button.");
        }
    }
}
