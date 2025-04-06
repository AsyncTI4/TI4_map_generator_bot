package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.TemporaryCombatModifierModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
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
        String headerText = player.getRepresentationUnfogged() + " Heads up, someone refreshed your Promissory Notes.";
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendPromissoryNoteInfo(game, player, longFormat);
    }

    public static String getPromissoryNoteCardInfo(Game game, Player player, boolean longFormat, boolean excludePlayArea) {
        StringBuilder sb = new StringBuilder();

        //PROMISSORY NOTES
        sb.append("### __Promissory notes in your hand__:").append("\n");
        int index = 1;
        Map<String, Integer> promissoryNotes = player.getPromissoryNotes();
        List<String> promissoryNotesInPlayArea = player.getPromissoryNotesInPlayArea();
        List<String> genericPromissoryNotes = Mapper.getColorPromissoryNoteIDs(game, player.getColor());

        if (promissoryNotes.isEmpty()) {
            return "## __Promissory notes__:\n> None";
        } else {
            for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                if (!promissoryNotesInPlayArea.contains(pn.getKey())) {
                    PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn.getKey());
                    sb.append(index++).append("\\. ").append(CardEmojis.PN).append("  _").append(pnModel.getName()).append("_ ");
                    Player pnOwner = game.getPNOwner(pn.getKey());
                    if (pnOwner == null) {
                        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation() + " one of your PNs has no owner. PN id is " + pn.getKey() + " and number is " + pn.getValue());
                        continue;
                    }
                    if (!game.isFowMode()) sb.append(pnOwner.getFactionEmoji());
                    sb.append(ColorEmojis.getColorEmojiWithName(pnOwner.getColor()));
                    sb.append(" `(").append(pn.getValue()).append(")`\n");
                    if (longFormat || pnOwner != player || !genericPromissoryNotes.contains(pn.getKey())) {
                        sb.append("> ").append(pnModel.getTextFormatted(game)).append("\n");
                    }
                }
            }

            if (!excludePlayArea) {
                //PLAY AREA PROMISSORY NOTES
                sb.append("\n").append("### __Promissory notes in your play area__:").append("\n");
                if (promissoryNotesInPlayArea.isEmpty()) {
                    sb.append("> None");
                } else {
                    for (Map.Entry<String, Integer> pn : promissoryNotes.entrySet()) {
                        if (promissoryNotesInPlayArea.contains(pn.getKey())) {
                            PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn.getKey());
                            sb.append(index++).append("\\. ").append(CardEmojis.PN).append("  _").append(pnModel.getName()).append("_ ");
                            Player pnOwner = game.getPNOwner(pn.getKey());
                            if (pnOwner == player) {
                                sb.append("✋");
                            } else {
                                if (!game.isFowMode()) sb.append(pnOwner.getFactionEmoji());
                                sb.append(ColorEmojis.getColorEmojiWithName(pnOwner.getColor()));
                            }
                            sb.append(" `(").append(pn.getValue()).append(")`\n> ").append(pnModel.getTextFormatted(game)).append("\n");
                        }
                    }
                }
            }
        }
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
        for (String promissoryNote : promissoryNotes) {
            player.setPromissoryNote(promissoryNote);
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
                MessageHelper.sendMessageToPlayerCardsInfoThread(player, player.getRepresentationUnfogged()
                    + " this promissory note will be applied automatically the next time you draw a relic."
                    + " It will not work if you play it before then, so I am stopping you here.");
                return;
            }
        }
        PromissoryNoteModel pn = Mapper.getPromissoryNote(id);
        String pnName = pn.getName();
        // String pnOwner = Mapper.getPromissoryNoteOwner(id);
        Player owner = game.getPNOwner(id);
        if (pn.getPlayArea() && !player.isPlayerMemberOfAlliance(owner)) {
            player.addPromissoryNoteToPlayArea(id);
        } else {
            player.removePromissoryNote(id);
            if (!"dspncymi".equalsIgnoreCase(id)) {
                owner.setPromissoryNote(id);
            }
            // PN Info is refreshed later
        }

        String emojiToUse = game.isFowMode() ? "" : owner.getFactionEmoji();
        String sb = player.getRepresentation() + " played _" + pnName + "_.";
        MessageEmbed pnEmbed = pn.getRepresentationEmbed();

        // Send the message up top before "resolving" so that buttons are at the bottom
        MessageHelper.sendMessageToChannelWithEmbed(player.getCorrectChannel(), sb, pnEmbed);
        if (game.isFowMode()) {
            // Fog of war ping for extra visibility
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, sb);
        }
        // And refresh cards info
        sendPromissoryNoteInfo(game, player, false);
        sendPromissoryNoteInfo(game, owner, false);
        MessageHelper.sendMessageToChannel(owner.getCardsInfoThread(), owner.getRepresentationUnfogged() + ", someone just played _" + pnName + "_.");

        if (id.contains("dspnveld")) {
            ButtonHelperFactionSpecific.offerVeldyrButtons(player, game, id);
        }
        if ("dspnolra".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.resolveOlradinPN(player, game, event);
        }
        if ("terraform".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerTerraformButtons(player, game, event);
        }
        if ("sigma_cyber".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.resolveSigmaLizixPN(player, game, event);
        }
        if ("dspnrohd".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerAutomatonsButtons(player, game, event);
        }
        if ("dspnbent".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerBentorPNButtons(player, game);
        }
        if ("dspnuyda".equalsIgnoreCase(id)) {
            List<Button> buttons = ButtonHelperCommanders.getUydaiCommanderButtons(game, true, player);
            String message = player.getRepresentationUnfogged() + " select which deck you wish to look at the top of.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
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
            String message = player.getRepresentationUnfogged() + " select planet you wish to use _Greyfire Mutagen_ on.";
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
            String message = player.getRepresentationUnfogged() + ", please select a planet to _Ragh's Call_ on.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message,
                ButtonHelperFactionSpecific.getRaghsCallButtons(player, game,
                    game.getTileByPosition(game.getActiveSystem())));
        }
        if ("sigma_raghs_call".equalsIgnoreCase(id)) {
            String message = player.getRepresentationUnfogged() + ", please select planet to _Ragh's Call_ on. You will need to ready the planet manually if applicable.";
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
                player.getRepresentation() + " drew an extra secret objective due to _Plots Within Plots_. Please discard an extra secret objective.");
        }
        if ("sigma_sycophancy".equalsIgnoreCase(id)) {
            game.drawSecretObjective(player.getUserID());
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " drew an extra secret objective due to _Sycophancy_. Please discard an extra secret objective.");
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
                        + " lost a command token from strategy pool due to a _Military Support_ play.");
            }
            String message = player.getRepresentationUnfogged() + " Use buttons to drop 2 infantry on a planet";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        }
        if (!"agendas_absol".equals(game.getAgendaDeckID()) && id.endsWith("_ps")) {
            if (game.playerHasLeaderUnlockedOrAlliance(owner, "xxchacommander")) {
                MessageHelper.sendMessageToChannel(owner.getCorrectChannel(), owner.getRepresentationUnfogged()
                    + ", due to a play of your _Political Secret_, you can't play action cards or use the abilities on your faction sheet."
                    + " You have also been automatically passed on \"whens\" and \"afters\".");
            } else {
                MessageHelper.sendMessageToChannel(owner.getCorrectChannel(), owner.getRepresentationUnfogged()
                    + ", due to a play of your _Political Secret_, you will be unable to vote in agenda."
                    + " You have also been automatically passed on \"whens\" and \"afters\".");
            }
            game.setStoredValue("queuedWhens", game.getStoredValue("queuedWhens").replace(owner.getFaction() + "_", ""));
            game.setStoredValue("declinedWhens", game.getStoredValue("declinedWhens") + owner.getFaction() + "_");
            game.setStoredValue("queuedAfters", game.getStoredValue("queuedAfters").replace(owner.getFaction() + "_", ""));
            game.setStoredValue("declinedAfters", game.getStoredValue("declinedAfters") + owner.getFaction() + "_");
            game.setStoredValue("queuedAftersLockedFor" + owner.getFaction(), "Yes");
            game.setStoredValue("AssassinatedReps",
                game.getStoredValue("AssassinatedReps") + owner.getFaction());
        }
        if ("fires".equalsIgnoreCase(id)) {
            player.addTech("ws");
            CommanderUnlockCheckService.checkPlayer(player, "mirveda");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentationUnfogged() + " acquired the War Sun technology.");
            owner.setFleetCC(owner.getFleetCC() - 1);
            String reducedMsg = owner.getRepresentationUnfogged()
                + ", 1 command token has been removed from your fleet pool because _Fires of the Gashlai_ was played.";
            ButtonHelper.checkFleetInEveryTile(owner, game, event);
            MessageHelper.sendMessageToChannel(owner.getCorrectChannel(), reducedMsg);
        }
        if ("sigma_fires".equalsIgnoreCase(id)) {
            player.addTech("ws");
            CommanderUnlockCheckService.checkPlayer(player, "mirveda");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentationUnfogged() + " acquired the War Sun technology.");
            ButtonHelper.checkFleetInEveryTile(owner, game, event);
            String reducedMsg = owner.getRepresentationUnfogged() + ", you must spend 1 command token due to _Fires of the Gashlai_ being played.";
            MessageHelper.sendMessageToChannelWithButtons(owner.getCorrectChannel(), reducedMsg, ButtonHelper.getLoseCCButtons(owner));

        }
        if (id.endsWith("_ta")) {
            int comms = owner.getCommodities();
            int oldTGs = player.getTg();
            owner.setCommodities(0);
            if (game.isFowMode()) {
                String reducedMsg = owner.getRepresentationUnfogged() + " your _Trade Agreement_ was played.";
                String reducedMsg2 = player.getRepresentationUnfogged()
                    + " you gained trade goods equal to the number of commodities the player had (your trade goods went from "
                    + oldTGs + " trade good" + (oldTGs == 1 ? "" : "s") + " to -> " + (oldTGs + comms)
                    + " trade good" + (oldTGs + comms == 1 ? "" : "s")
                    + "). Please follow up with the player if this number seems off.";
                player.setTg(oldTGs + comms);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, owner, player, owner.getCommoditiesTotal());
                ButtonHelperAbilities.pillageCheck(player, game);
                MessageHelper.sendMessageToChannel(owner.getCorrectChannel(), reducedMsg);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), reducedMsg2);
            } else {
                String reducedMsg = owner.getRepresentationUnfogged() + " your _Trade Agreement_ was played.";
                String reducedMsg2 = player.getRepresentationUnfogged() + " played the _Trade Agreement_ belonging to "
                    + owner.getRepresentationUnfogged() + ", taking their " + comms + " commodit" + (comms == 1 ? "y" : "ies")
                    + " ("
                    + oldTGs + " tg" + (oldTGs == 1 ? "" : "s") + " -> " + (oldTGs + comms) + "tg"
                    + (oldTGs + comms == 1 ? "" : "s") + ").";
                player.setTg(oldTGs + comms);
                ButtonHelperFactionSpecific.resolveDarkPactCheck(game, owner, player, owner.getCommoditiesTotal());
                ButtonHelperAbilities.pillageCheck(player, game);
                MessageHelper.sendMessageToChannel(owner.getCorrectChannel(), reducedMsg);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), reducedMsg2);
            }
        }
        if (("favor".equalsIgnoreCase(id))) {
            if (owner.getStrategicCC() > 0) {
                owner.setStrategicCC(owner.getStrategicCC() - 1);
                String reducedMsg = owner.getRepresentationUnfogged()
                    + ", 1 command token has been removed from your strategy pool because _Political Favor_ was played.";
                if (game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(owner.getPrivateChannel(), reducedMsg);
                } else {
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), reducedMsg);
                }
                AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    "_Political Favor_ has been played to discard the current agenda.");
            } else if (owner.getFaction().equalsIgnoreCase("xxcha")) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "The Xxcha player does not have any command tokens in their strategy pool."
                        + " As such, _Political Favor_ cannot be resolved and the current agenda remains.");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "The owner of _Political Favor_ does not have any command tokens in their strategy pool."
                        + " As such, _Political Favor_ cannot be resolved and the current agenda remains.");
            }
        }
        if (("scepter".equalsIgnoreCase(id))) {
            String message = player.getRepresentationUnfogged() + ", please choose which system to Mahact Diplo.";
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
            MessageHelper.sendMessageToChannelWithFactionReact(player.getCorrectChannel(), player.getRepresentation() +
                "Please select your Rider target", game, player, riderButtons);
            //MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
            //    "Please indicate \"no afters\" again.", game, afterButtons, GameMessageType.AGENDA_AFTER);

        }
        if ("dspnedyn".equalsIgnoreCase(id)) {
            String riderName = "Edyn Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            //List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            MessageHelper.sendMessageToChannelWithFactionReact(player.getCorrectChannel(), player.getRepresentation() +
                "Please select your Rider target", game, player, riderButtons);
            //MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
            //    "Please indicate \"no afters\" again.", game, afterButtons, GameMessageType.AGENDA_AFTER);
        }
        if ("dspnkyro".equalsIgnoreCase(id)) {
            String riderName = "Kyro Rider";
            String finsFactionCheckerPrefix = "FFCC_" + player.getFaction() + "_";

            List<Button> riderButtons = AgendaHelper.getAgendaButtons(riderName, game, finsFactionCheckerPrefix);
            //List<Button> afterButtons = AgendaHelper.getAfterButtons(game);
            MessageHelper.sendMessageToChannelWithFactionReact(player.getCorrectChannel(), player.getRepresentation() +
                "Please select your Rider target", game, player, riderButtons);
            //MessageHelper.sendMessageToChannelWithPersistentReacts(game.getMainGameChannel(),
            //    "Please indicate \"no afters\" again.", game, afterButtons, GameMessageType.AGENDA_AFTER);
        }
        if ("spynet".equalsIgnoreCase(id)) {
            ButtonHelperFactionSpecific.offerSpyNetOptions(player);
        }
        if ("dspnlane".equalsIgnoreCase(id)) {
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + ", please use buttons to explore a planet you control.", buttons);
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

                numToBeat -= 1;

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
        if ("sigma_excavation_experts".equalsIgnoreCase(id)) {
            List<Button> exploreButtons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + ", explore each planet that contains your mechs.", exploreButtons);
        }
        if ("sigma_primitivism".equalsIgnoreCase(id)) {
            Button transact2 = Buttons.green("gain_CC", "Gain 1 Command Token");
            int oldTgs = player.getTg();
            player.setTg(oldTgs + 4);
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(),
                player.getRepresentation() + ", you have gained 1 command token and 4 trade goods (" + oldTgs
                    + " -> " + (oldTgs + 4) + ") from playing _Primitivism_. Please use the button to gain your command token.",
                transact2);
        }
        if (pn.getText().toLowerCase().contains("action:") && !"acq".equalsIgnoreCase(id)) {
            ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        }
        TemporaryCombatModifierModel possibleCombatMod = CombatTempModHelper.getPossibleTempModifier(Constants.PROMISSORY_NOTES, pn.getAlias(), player.getNumberOfTurns());
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

        MessageHelper.sendMessageToPlayerCardsInfoThread(targetPlayer, sb.toString());
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, "All PNs shown to player");
    }

    public void sendRandom(GenericInteractionCreateEvent event, Game game, Player sourcePlayer, Player targetPlayer) {
        Map<String, Integer> promissoryNoteCounts = sourcePlayer.getPromissoryNotes();
        List<String> promissoryNotes = new ArrayList<>(promissoryNoteCounts.keySet());
        if (promissoryNotes.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No Promissory Notes in hand");
        }
        Collections.shuffle(promissoryNotes);
        String promissoryNoteId = promissoryNotes.getFirst();
        if (game.isFowMode()) {
            FoWHelper.pingPlayersTransaction(game, event, sourcePlayer, targetPlayer, CardEmojis.ActionCard + " Action Card", null);
        }

        sourcePlayer.removePromissoryNote(promissoryNoteCounts.get(promissoryNoteId));
        sendPromissoryNoteInfo(game, sourcePlayer, false);
        MessageHelper.sendMessageToChannel(sourcePlayer.getCardsInfoThread(), "# " + sourcePlayer.getRepresentation() + " you lost the promissory note _" + Mapper.getPromissoryNote(promissoryNoteId).getName() + "_.");

        targetPlayer.setPromissoryNote(promissoryNoteId);
        sendPromissoryNoteInfo(game, targetPlayer, false);

        MessageHelper.sendMessageToChannel(targetPlayer.getCardsInfoThread(), "# " + targetPlayer.getRepresentation() + " you gained the promissory note _" + Mapper.getPromissoryNote(promissoryNoteId).getName() + "_.");
    }
}
