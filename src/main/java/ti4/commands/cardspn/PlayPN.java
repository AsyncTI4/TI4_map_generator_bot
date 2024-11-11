package ti4.commands.cardspn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.commands.cardsac.PickACFromDiscard;
import ti4.commands.game.StartPhase;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands.units.AddUnits;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.TechnologyModel;
import ti4.model.TemporaryCombatModifierModel;

public class PlayPN extends PlayerGameStateSubcommand {

    public PlayPN() {
        super(Constants.PLAY_PN, "Play Promissory Note", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory Note ID that is sent between () or Name/Part of Name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = Helper.getPlayerFromGame(game, event, event.getUser().getId());

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

        playPN(event, game, player, pnID);
    }

    private void playPN(GenericInteractionCreateEvent event, Game game, Player player, String pnID) {
        resolvePNPlay(pnID, player, game, event);
    }

    public static void resolvePNPlay(String id, Player player, Game game, GenericInteractionCreateEvent event) {
        boolean longPNDisplay = false;
        boolean fromHand = true;
        if ("bmfNotHand".equals(id)) {
            fromHand = false;
            id = "bmf";
        }

        if (id.contains("dspnflor")) {
            if (id.contains("Checked")) {
                id = "dspnflor";
            } else {
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, player.getRepresentationUnfogged()
                    + " this PN will be applied automatically the next time you draw a relic. It will not work if you play it before then, so I am stopping you here");
                return;
            }
        }
        PromissoryNoteModel pn = Mapper.getPromissoryNote(id);
        String pnName = pn.getName();
        // String pnOwner = Mapper.getPromissoryNoteOwner(id);
        Player owner = game.getPNOwner(id);
        if (pn.getPlayArea() && !player.isPlayerMemberOfAlliance(owner)) {
            player.setPromissoryNotesInPlayArea(id);
        } else {
            player.removePromissoryNote(id);
            if (!"dspncymi".equalsIgnoreCase(id)) {
                owner.setPromissoryNote(id);
            }
            // PN Info is refreshed later
        }

        String emojiToUse = game.isFowMode() ? "" : owner.getFactionEmoji();
        StringBuilder sb = new StringBuilder(player.getRepresentation() + " played promissory note: " + pnName + "\n");
        sb.append(emojiToUse).append(Emojis.PN);
        String pnText;

        // Handle AbsolMode Political Secret
        if (game.isAbsolMode() && id.endsWith("_ps")) {
            pnText = "Political Secret" + Emojis.Absol
                + ":  *When you cast votes:* You may exhaust up to 3 of the {color} player's planets and cast additional votes equal to the combined influence value of the exhausted planets. Then return this card to the {color} player.";
        } else {
            pnText = longPNDisplay ? Mapper.getPromissoryNote(id).getText() : Mapper.getPromissoryNote(id).getName();
        }
        sb.append(pnText).append("\n");

        // Send the message up top before "resolving" so that buttons are at the bottom
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        if (game.isFowMode()) {
            // Fog of war ping for extra visibility
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, sb.toString());
        }
        // And refresh cards info
        PNInfo.sendPromissoryNoteInfo(game, player, false);
        PNInfo.sendPromissoryNoteInfo(game, owner, false);
        MessageHelper.sendMessageToChannel(owner.getCardsInfoThread(), owner.getRepresentationUnfogged() + " someone played one of your PNs (" + pnName + ")");

        if (id.contains("dspnveld")) {
            ButtonHelperFactionSpecific.offerVeldyrButtons(player, game, id);
        }
        if ("dspnolra".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.resolveOlradinPN(player, game, event);
        }
        if ("terraform".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerTerraformButtons(player, game, event);
        }
        if ("dspnrohd".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerAutomatonsButtons(player, game, event);
        }
        if ("dspnbent".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerBentorPNButtons(player, game, event);
        }
        if ("dspngled".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerGledgeBaseButtons(player, game, event);
        }
        if ("iff".equalsIgnoreCase(id)) {
            List<Button> buttons = new ArrayList<>(ButtonHelperFactionSpecific.getCreussIFFTypeOptions());
            String message = player.getRepresentationUnfogged() + " select type of wormhole you wish to drop";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        if ("greyfire".equalsIgnoreCase(id)) {
            List<Button> buttons = ButtonHelperFactionSpecific.getGreyfireButtons(game);
            String message = player.getRepresentationUnfogged() + " select planet you wish to use greyfire on";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        if ("dspnlizh".equalsIgnoreCase(id) || "dspnchei".equalsIgnoreCase(id)) {
            new AddUnits().unitParsing(event, player.getColor(),
                game.getTileByPosition(game.getActiveSystem()), "2 ff", game);
            String message = player.getRepresentationUnfogged() + " added 2 fighters to the active system";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
        if ("dspncymi".equalsIgnoreCase(id)) {
            PickACFromDiscard.pickACardFromDiscardStep1(event, game, player);
        }
        if ("dspnkort".equalsIgnoreCase(id)) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "kortalipn");
            MessageChannel channel = player.getCorrectChannel();
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        }
        if ("ragh".equalsIgnoreCase(id)) {
            String message = player.getRepresentationUnfogged() + " select planet to Ragh's Call on";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message,
                ButtonHelperFactionSpecific.getRaghsCallButtons(player, game,
                    game.getTileByPosition(game.getActiveSystem())));
        }
        if ("cavalry".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.resolveCavStep1(game, player);
        }
        if ("dspntnel".equalsIgnoreCase(id)) {
            game.drawSecretObjective(player.getUserID());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " drew an extra SO due to Tnelis PN. Please discard an extra SO");
        }
        if ("dspnvade".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.resolveVadenTgForSpeed(player, game, event);
        }
        if ("crucible".equalsIgnoreCase(id)) {
            game.setStoredValue("crucibleBoost", "2");
        }
        if ("ms".equalsIgnoreCase(id)) {
            List<Button> buttons = new ArrayList<>(
                Helper.getPlanetPlaceUnitButtons(player, game, "2gf", "placeOneNDone_skipbuild"));
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                MessageHelper.sendMessageToChannel(owner.getCorrectChannel(),
                    owner.getRepresentationUnfogged()
                        + " lost a command counter from strategy pool due to a Military Support play");
            }
            String message = player.getRepresentationUnfogged() + " Use buttons to drop 2 infantry on a planet";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        if (!"agendas_absol".equals(game.getAgendaDeckID()) && id.endsWith("_ps")) {
            MessageHelper.sendMessageToChannel(owner.getCorrectChannel(), owner.getRepresentationUnfogged()
                + " due to a play of your Political Secret, you will be unable to vote in agenda (unless you have Xxcha alliance). The bot doesn't enforce the other restrictions regarding no abilities, but you should abide by them.");
            game.setStoredValue("AssassinatedReps",
                game.getStoredValue("AssassinatedReps") + owner.getFaction());
        }
        if ("fires".equalsIgnoreCase(id)) {
            player.addTech("ws");
            CommanderUnlockCheck.checkPlayer(player, "mirveda");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentationUnfogged() + " acquired War Sun tech");
            owner.setFleetCC(owner.getFleetCC() - 1);
            ButtonHelper.checkFleetInEveryTile(owner, game, event);
            String reducedMsg = owner.getRepresentationUnfogged() + " reduced your fleet CC by 1 due to fires being played";
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
            } else {
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), reducedMsg);
            }
        }
        if (id.endsWith("_ta")) {
            int comms = owner.getCommodities();
            owner.setCommodities(0);
            String reducedMsg = owner.getRepresentationUnfogged() + " your TA was played.";
            String reducedMsg2 = player.getRepresentationUnfogged()
                + " you gained TGs equal to the number of comms the player had (your TGs went from "
                + player.getTg() + "TG" + (player.getTg() == 1 ? "" : "s") + " to -> " + (player.getTg() + comms)
                + "TG" + (player.getTg() + comms == 1 ? "" : "s")
                + "). Please follow up with the player if this number seems off.";
            player.setTg(player.getTg() + comms);
            ButtonHelperFactionSpecific.resolveDarkPactCheck(game, owner, player, owner.getCommoditiesTotal());
            ButtonHelperAbilities.pillageCheck(player, game);
            MessageHelper.sendMessageToChannel(owner.getCorrectChannel(), reducedMsg);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), reducedMsg2);
        }
        if (("favor".equalsIgnoreCase(id))) {
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                String reducedMsg = owner.getRepresentationUnfogged()
                    + " reduced your strategy CC by 1 due to your PN getting played";
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
                } else {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), reducedMsg);
                }
                AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    "Political Favor (xxcha PN) was played");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "PN owner did not have a strategy CC, agenda not vetod");
            }
        }
        if (("scepter".equalsIgnoreCase(id))) {
            String message = player.getRepresentationUnfogged() + " Use buttons choose which system to mahact diplo";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
                Helper.getPlanetSystemDiploButtons(player, game, false, owner));
        }
        if (("dspnkoll".equalsIgnoreCase(id))) {
            ButtonHelperFactionSpecific.offerKolleccPNButtons(game, player);
        }
        if (id.contains("rider")) {
            String riderName = "Keleres Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            MessageHelper.sendMessageToChannelWithFactionReact(game.getMainGameChannel(),
                "Please select your Rider target", game, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
                "Please indicate \"no afters\" again.", game, afterButtons, "after");

        }
        if ("dspnedyn".equalsIgnoreCase(id)) {
            String riderName = "Edyn Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            MessageHelper.sendMessageToChannelWithFactionReact(game.getMainGameChannel(),
                "Please select your Rider target", game, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
                "Please indicate \"no afters\" again.", game, afterButtons, "after");
        }
        if ("dspnkyro".equalsIgnoreCase(id)) {
            String riderName = "Kyro Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            MessageHelper.sendMessageToChannelWithFactionReact(game.getMainGameChannel(),
                "Please select your Rider target", game, player, riderButtons);
            MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
                "Please indicate \"no afters\" again.", game, afterButtons, "after");
        }
        if ("spynet".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerSpyNetOptions(player);
        }
        if ("gift".equalsIgnoreCase(id)) {
            StartPhase.startActionPhase(event, game);
            //in case Naalu gets eliminated and the PN goes away
            game.setStoredValue("naaluPNUser", player.getFaction());
        }
        if ("bmf".equalsIgnoreCase(id)) {
            if (fromHand) {
                String finChecker = "";
                String message = "Click the fragments you'd like to purge. ";
                List<Button> purgeFragButtons = new ArrayList<>();
                int numToBeat = 2 - player.getUrf();

                numToBeat = numToBeat - 1;

                if (player.getCrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getCrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.blue(finChecker + "purge_Frags_CRF_" + x,
                            "Cultural Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (player.getIrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getIrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.green(finChecker + "purge_Frags_IRF_" + x,
                            "Industrial Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                if (player.getHrf() > numToBeat) {
                    for (int x = numToBeat + 1; (x < player.getHrf() + 1 && x < 4); x++) {
                        Button transact = Buttons.red(finChecker + "purge_Frags_HRF_" + x,
                            "Hazardous Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }

                if (player.getUrf() > 0) {
                    for (int x = 1; x < player.getUrf() + 1; x++) {
                        Button transact = Buttons.gray(finChecker + "purge_Frags_URF_" + x,
                            "Frontier Fragments (" + x + ")");
                        purgeFragButtons.add(transact);
                    }
                }
                Button transact2 = Buttons.red(finChecker + "drawRelicFromFrag", "Finish Purging and Draw Relic");
                if (player.hasAbility("a_new_edifice")) {
                    transact2 = Buttons.red(finChecker + "drawRelicFromFrag", "Finish Purging and Explore");
                }
                purgeFragButtons.add(transact2);
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
                    purgeFragButtons);
            }
        }
        if (pn.getText().toLowerCase().contains("action:") && !"acq".equalsIgnoreCase(id)) {
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        }
        TemporaryCombatModifierModel posssibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.PROMISSORY_NOTES, pn.getAlias(), player.getNumberTurns());
        if (posssibleCombatMod != null) {
            player.addNewTempCombatMod(posssibleCombatMod);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Combat modifier will be applied next time you push the combat roll button.");
        }
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
        resolvePNPlay(pnID, player, game, event);
        if (!"bmfNotHand".equalsIgnoreCase(pnID)) {
            ButtonHelper.deleteMessage(event);
        }

        var posssibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.PROMISSORY_NOTES, pnID, player.getNumberTurns());
        if (posssibleCombatMod != null) {
            player.addNewTempCombatMod(posssibleCombatMod);
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), "Combat modifier will be applied next time you push the combat roll button.");
        }
    }
}
