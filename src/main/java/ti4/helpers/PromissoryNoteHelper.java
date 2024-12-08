package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.Source;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.game.StartPhaseService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class PromissoryNoteHelper {

    public static void sendPromissoryNoteInfo(Game game, Player player, boolean longFormat) {
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                getPromissoryNoteCardInfo(game, player, longFormat, false),
                getPNButtons(game, player));
    }

    public static void sendPromissoryNoteInfo(Game game, Player player, boolean longFormat, GenericInteractionCreateEvent event) {
        checkAndAddPNs(game, player);
        game.checkPromissoryNotes();
        String headerText = player.getRepresentationUnfogged() + " Heads up, someone used some command";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendPromissoryNoteInfo(game, player, longFormat);
    }

    public static String getPromissoryNoteCardInfo(Game game, Player player, boolean longFormat, boolean excludePlayArea) {
        StringBuilder sb = new StringBuilder();

        //PROMISSORY NOTES
        sb.append("**Promissory Notes:**").append("\n");
        int index = 1;
        Map<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        if (promissoryNotes == null) {
            return sb.toString();
        }

        if (promissoryNotes.isEmpty()) {
            sb.append("> None");
        } else {
            for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                    sb.append("> `").append(index).append(".").append(Helper.leftpad("(" + pn.getValue(), 3)).append(")`");
                    sb.append(getPromissoryNoteRepresentation(game, pn.getKey(), longFormat));
                    index++;
                }
            }

            if (!excludePlayArea) {
                //PLAY AREA PROMISSORY NOTES
                sb.append("\n\n").append("__**PLAY AREA Promissory Notes:**__").append("\n");
                if (promissoryNotesInPlayArea.isEmpty()) {
                    sb.append("> None");
                } else {
                    for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                        if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                            sb.append("`").append(index).append(".");
                            sb.append("(").append(pn.getValue()).append(")`");
                            sb.append(getPromissoryNoteRepresentation(game, pn.getKey(), longFormat));
                            index++;
                        }
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String getPromissoryNoteRepresentation(Game game, String pnID) {
        return getPromissoryNoteRepresentation(game, pnID, true);
    }

    public static String getPromissoryNoteRepresentation(Game game, String pnID, boolean longFormat) {
        PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pnID);
        if (pnModel == null) {
            String error = "Could not find representation for PN ID: " + pnID;
            BotLogger.log(error);
            return error;
        }
        String pnName = pnModel.getName();
        StringBuilder sb = new StringBuilder();

        sb.append(Emojis.PN);
        if (pnModel.getFaction().isPresent()) sb.append(Emojis.getFactionIconFromDiscord(pnModel.getFaction().get()));
        sb.append("__**").append(pnName).append("**__");
        sb.append(pnModel.getSource().emoji());
        sb.append("   ");

        String pnText = pnModel.getText();
        Player pnOwner = game.getPNOwner(pnID);
        if (pnOwner != null && pnOwner.isRealPlayer()) {
            if (!game.isFowMode()) sb.append(pnOwner.getFactionEmoji());
            sb.append(Emojis.getColorEmojiWithName(pnOwner.getColor()));
            pnText = pnText.replaceAll(pnOwner.getColor(), Emojis.getColorEmojiWithName(pnOwner.getColor()));
        }

        if (longFormat ||
                Mapper.isValidFaction(pnModel.getFaction().orElse("").toLowerCase()) ||
                (pnModel.getSource() != Source.ComponentSource.base && pnModel.getSource() != Source.ComponentSource.pok)) {
            sb.append("      ").append(pnText);
        }
        sb.append("\n");
        return sb.toString();
    }

    public static void checkAndAddPNs(Game game, Player player) {
        String playerColor = AliasHandler.resolveColor(player.getColor());
        String playerFaction = player.getFaction();
        if (!Mapper.isValidColor(playerColor) || !Mapper.isValidFaction(playerFaction)) {
            return;
        }

        // All PNs a Player brought to the game (owns)
        List<String> promissoryNotes = new ArrayList<>(player.getPromissoryNotesOwned());

        // Remove PNs in other players' hands and player areas and purged PNs
        for (Player player_ : game.getPlayers().values()) {
            promissoryNotes.removeAll(player_.getPromissoryNotes().keySet());
            promissoryNotes.removeAll(player_.getPromissoryNotesInPlayArea());
        }
        promissoryNotes.removeAll(player.getPromissoryNotes().keySet());
        promissoryNotes.removeAll(player.getPromissoryNotesInPlayArea());
        promissoryNotes.removeAll(game.getPurgedPN());

        // Any remaining PNs are missing from the game and can be re-added to the player's hand
        if (!promissoryNotes.isEmpty()) {
            for (String promissoryNote : promissoryNotes) {
                player.setPromissoryNote(promissoryNote);
            }
        }
    }

    public static List<Button> getPNButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String pnShortHand : player.getPromissoryNotes().keySet()) {
            if (player.getPromissoryNotesInPlayArea().contains(pnShortHand)) {
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnShortHand);
            Player owner = game.getPNOwner(pnShortHand);
            if (owner == player || pnShortHand.endsWith("_ta")) {
                continue;
            }

            Button transact;
            if (game.isFowMode()) {
                transact = Buttons.green("resolvePNPlay_" + pnShortHand,
                        "Play " + owner.getColor() + " " + promissoryNote.getName());
            } else {
                transact = Buttons.green("resolvePNPlay_" + pnShortHand, "Play " + promissoryNote.getName()).withEmoji(Emoji.fromFormatted(owner.getFactionEmoji()));
            }
            buttons.add(transact);
        }
        return buttons;
    }

    public static void resolvePNPlay(String id, Player player, Game game, GenericInteractionCreateEvent event) {
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
            pnText = Mapper.getPromissoryNote(id).getName();
        }
        sb.append(pnText).append("\n");

        // Send the message up top before "resolving" so that buttons are at the bottom
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        if (game.isFowMode()) {
            // Fog of war ping for extra visibility
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, sb.toString());
        }
        // And refresh cards info
        sendPromissoryNoteInfo(game, player, false);
        sendPromissoryNoteInfo(game, owner, false);
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
            ButtonHelperFactionSpecific.offerBentorPNButtons(player, game);
        }
        if ("dspngled".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerGledgeBaseButtons(player, game);
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
            Tile tile = game.getTileByPosition(game.getActiveSystem());
            AddUnitService.addUnits(event, tile, game, player.getColor(), "2 ff");
            String message = player.getRepresentationUnfogged() + " added 2 fighters to the active system";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
        if ("dspncymi".equalsIgnoreCase(id)) {
            ActionCardHelper.pickACardFromDiscardStep1(game, player);
        }
        if ("dspnkort".equalsIgnoreCase(id)) {
            List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "kortalipn");
            MessageChannel channel = player.getCorrectChannel();
            MessageHelper.sendMessageToChannelWithButtons(channel, "Use buttons to remove token.", buttons);
        }
        if ("ragh".equalsIgnoreCase(id)) {
            String message = player.getRepresentationUnfogged() + " select planet to Ragh's Call on";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
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
            ButtonHelperFactionSpecific.resolveVadenTgForSpeed(player, event);
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
            CommanderUnlockCheckService.checkPlayer(player, "mirveda");
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
            StartPhaseService.startActionPhase(event, game);
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
        TemporaryCombatModifierModel possibleCombatMod = CombatTempModHelper.getPossibleTempModifier(Constants.PROMISSORY_NOTES, pn.getAlias(), player.getNumberTurns());
        if (possibleCombatMod != null) {
            player.addNewTempCombatMod(possibleCombatMod);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Combat modifier will be applied next time you push the combat roll button.");
        }
    }

    public void showAll(Player player, Player targetPlayer, Game game) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(game.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Promissory Notes:").append("\n");
        List<String> promissoryNotes = new ArrayList<>(player.getPromissoryNotes().keySet());
        Collections.shuffle(promissoryNotes);
        int index = 1;
        for (String id : promissoryNotes) {
            sb.append(index).append(". ").append(Mapper.getPromissoryNote(id).getName()).append(" (original owner ").append(game.getPNOwner(id).getFactionEmojiOrColor()).append(")").append("\n");
            index++;
        }

        MessageHelper.sendMessageToPlayerCardsInfoThread(targetPlayer, game, sb.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, "All PNs shown to player");
    }
}
