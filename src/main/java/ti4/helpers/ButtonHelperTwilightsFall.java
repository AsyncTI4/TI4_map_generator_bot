package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardModel;
import ti4.model.UnitModel;
import ti4.service.button.ReactionService;

public class ButtonHelperTwilightsFall {

    // @ButtonHandler("initiateASplice_")
    public static void initiateASplice(
            Game game, Player startPlayer, String buttonID, List<Player> participants, int modifiers) {
        String spliceType = buttonID;
        if (buttonID.contains("_")) {
            spliceType = buttonID.split("_")[1];
        }
        int size = 1 + participants.size() + modifiers;
        game.setStoredValue("spliceType", spliceType);
        game.removeStoredValue("savedParticipants");
        setNewSpliceCards(game, spliceType, size);
        sendPlayerSpliceOptions(game, startPlayer);
        for (Player p : participants) {
            if (game.getStoredValue("savedParticipants").isEmpty()) {
                game.setStoredValue("savedParticipants", p.getFaction());
            } else {
                game.setStoredValue(
                        "savedParticipants", game.getStoredValue("savedParticipants") + "_" + p.getFaction());
            }
        }
        MessageHelper.sendMessageToChannel(
                game.getActionsChannel(),
                "A splice has started, buttons have been sent to " + startPlayer.getRepresentation()
                        + "'s cards and info thread to select a card.");
    }

    public static List<String> getSpliceCards(Game game) {
        return List.of(game.getStoredValue("savedSpliceCards").split("_"));
    }

    public static List<Player> getParticipantsList(Game game) {
        List<Player> players = new ArrayList<>();
        for (String faction : game.getStoredValue("savedParticipants").split("_")) {
            Player p = game.getPlayerFromColorOrFaction(faction);
            if (p != null) {
                players.add(p);
            }
        }
        return players;
    }

    public static void sendPlayerSpliceOptions(Game game, Player player) {
        String type = game.getStoredValue("spliceType");
        List<String> cards = getSpliceCards(game);
        List<Button> buttons = getSpliceButtons(game, type, cards);
        List<MessageEmbed> embeds = getSpliceEmbeds(game, type, cards);
        String msg = player.getRepresentation() + " Select a card to splice into your faction:";
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(player.getCardsInfoThread(), msg, embeds, buttons);
    }

    @ButtonHandler("participateInSplice_")
    public static void participateInSplice(Game game, Player player, String buttonID, ButtonInteractionEvent event) {

        int splice = Integer.parseInt(buttonID.split("_")[1]);
        game.setStoredValue("willParticipateInSplice", game.getStoredValue("willParticipateInSplice") + "_" + player.getFaction());
        ButtonHelperSCs.scFollow(game, player, event, buttonID);

        //Some message in SC thread to say they are participating?


    }

    @ButtonHandler("startSplice_")
    public static void startSplice(Game game, Player player, String buttonID, ButtonInteractionEvent event) {

        int splice = Integer.parseInt(buttonID.split("_")[1]);
        for(Player p : game.getRealPlayers()) {
            if (!p.getFollowedSCs().contains(splice) && !p.getFaction().equals(player.getFaction())) {
                MessageHelper.sendEphemeralMessageToEventChannel(event, p.getRepresentation() + " has not yet chosen whether to participate in the splice, so the splice cannot proceed.");
                return;
            }
        }
        List<Player> participants = new ArrayList<>();
        List<Player> fullOrder = Helper.getSpeakerOrFullPriorityOrderFromPlayer(player, game);
        if(game.getStoredValue("reverseSpliceOrder").isEmpty()){
            Collections.reverse(participants);
            Collections.rotate(fullOrder, -1);
        }
        for(Player p : fullOrder){
            if (game.getStoredValue("willParticipateInSplice").contains(p.getFaction()) || p.getFaction().equals(player.getFaction())) {
                participants.add(p);
            }
        }
        

    }

    @ButtonHandler("selectASpliceCard_")
    public static void selectASpliceCard(Game game, Player player, String buttonID, ButtonInteractionEvent event) {
        String cardID = buttonID.split("_")[1];
        String type = game.getStoredValue("spliceType");
        if (type.equalsIgnoreCase("ability")) {
            player.addTech(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has spliced in the ability: "
                            + Mapper.getTech(cardID).getName(),
                    Mapper.getTech(cardID).getRepresentationEmbed());
        }
        if (type.equalsIgnoreCase("genome")) {
            player.addLeader(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has spliced in the genome: "
                            + Mapper.getLeader(cardID).getName(),
                    Mapper.getLeader(cardID).getRepresentationEmbed());
        }
        if (type.equalsIgnoreCase("units")) {
            player.addOwnedUnitByID(cardID);
            MessageHelper.sendMessageToChannelWithEmbed(
                    game.getActionsChannel(),
                    player.getRepresentation() + " has spliced in the unit: "
                            + Mapper.getUnit(cardID).getName(),
                    Mapper.getUnit(cardID).getRepresentationEmbed());
        }
        List<Player> participants = getParticipantsList(game);
        participants.remove(player);
        if (participants.size() > 0) {
            sendPlayerSpliceOptions(game, participants.get(0));
            participants.remove(0);
            game.removeStoredValue("savedParticipants");
            for (Player p : participants) {
                if (game.getStoredValue("savedParticipants").isEmpty()) {
                    game.setStoredValue("savedParticipants", p.getFaction());
                } else {
                    game.setStoredValue(
                            "savedParticipants", game.getStoredValue("savedParticipants") + "_" + p.getFaction());
                }
            }
        } else {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "The splice is complete.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawParadigm")
    public static void drawParadigm(Game game, Player player, ButtonInteractionEvent event) {

        String messageID = event.getMessageId();
        boolean used = ButtonHelperSCs.addUsedSCPlayer(messageID, game, player);
        StrategyCardModel scModel = null;
        for (int scNum : player.getUnfollowedSCs()) {
            if (game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("pok8imperial")
                    || game.getStrategyCardModelByInitiative(scNum).get().usesAutomationForSCID("tf8")) {
                scModel = game.getStrategyCardModelByInitiative(scNum).get();
            }
        }

        if (!player.getFollowedSCs().contains(scModel.getInitiative())) {
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scModel.getInitiative(), game, event);
        }
        if (!used
                && (scModel.usesAutomationForSCID("pok8imperial") || scModel.usesAutomationForSCID("tf8"))
                && !player.getFollowedSCs().contains(scModel.getInitiative())
                && game.getPlayedSCs().contains(scModel.getInitiative())) {
            int scNum = scModel.getInitiative();
            player.addFollowedSC(scNum, event);
            ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
            if (player.getStrategicCC() > 0) {
                ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "followed **Aeterna**");
            }
            String message = ButtonHelperSCs.deductCC(game, player, scNum);
            ReactionService.addReaction(event, game, player, message);
        }

        List<String> allCards = Mapper.getDeck("tf_paradigm").getNewShuffledDeck();
        List<String> alreadyDrawn =
                List.of(game.getStoredValue("savedParadigms").split("_"));
        for (String card : alreadyDrawn) {
            allCards.remove(card);
        }
        String leader = allCards.remove(0);
        if (game.getStoredValue("savedParadigms").isEmpty()) {
            game.setStoredValue("savedParadigms", leader);
        } else {
            game.setStoredValue("savedParadigms", game.getStoredValue("savedParadigms") + "_" + leader);
        }
        MessageHelper.sendMessageToChannelWithEmbed(
                game.getActionsChannel(),
                player.getRepresentation() + " has drawn a new paradigm: "
                        + Mapper.getLeader(leader).getName(),
                Mapper.getLeader(leader).getRepresentationEmbed());
        player.addLeader(leader);
        player.getLeaderByID(leader).get().setLocked(false);
    }

    public static List<Button> getSpliceButtons(Game game, String type, List<String> cards) {
        List<Button> buttons = new ArrayList<>();
        if (type.equalsIgnoreCase("ability")) {
            for (String card : cards) {
                String name = Mapper.getTech(card).getName();
                buttons.add(Buttons.green(
                        "selectASpliceCard_" + card,
                        "Select " + name,
                        Mapper.getTech(card).getSingleTechEmoji()));
            }
        }
        if (type.equalsIgnoreCase("genome")) {
            for (String card : cards) {
                String name = Mapper.getLeader(card).getName();
                String faction = Mapper.getLeader(card).getFaction();
                FactionModel factionModel = Mapper.getFaction(faction);
                buttons.add(
                        Buttons.green("selectASpliceCard_" + card, "Select " + name, factionModel.getFactionEmoji()));
            }
        }
        if (type.equalsIgnoreCase("units")) {
            for (String card : cards) {
                String name = Mapper.getUnit(card).getName();
                buttons.add(Buttons.green(
                        "selectASpliceCard_" + card,
                        "Select " + name,
                        Mapper.getUnit(card).getUnitEmoji()));
            }
        }
        return buttons;
    }

    public static List<MessageEmbed> getSpliceEmbeds(Game game, String type, List<String> cards) {
        List<MessageEmbed> embeds = new ArrayList<>();
        if (type.equalsIgnoreCase("ability")) {
            for (String card : cards) {
                embeds.add(Mapper.getTech(card).getRepresentationEmbed());
            }
        }
        if (type.equalsIgnoreCase("genome")) {
            for (String card : cards) {
                embeds.add(Mapper.getLeader(card).getRepresentationEmbed());
            }
        }
        if (type.equalsIgnoreCase("units")) {
            for (String card : cards) {
                embeds.add(Mapper.getUnit(card).getRepresentationEmbed());
            }
        }
        return embeds;
    }

    public static void setNewSpliceCards(Game game, String type, int size) {
        List<String> cards = new ArrayList<>();
        if (type.equalsIgnoreCase("ability")) {
            List<String> allCards = Mapper.getDeck("techs_tf").getNewShuffledDeck();
            for (Player p : game.getRealPlayers()) {
                for (String tech : p.getTechs()) {
                    allCards.remove(tech);
                }
            }
            for (int i = 0; i < size && allCards.size() > 0; i++) {
                cards.add(allCards.remove(0));
            }
        }
        if (type.equalsIgnoreCase("genome")) {
            List<String> allCards = Mapper.getDeck("tf_genome").getNewShuffledDeck();
            for (Player p : game.getRealPlayers()) {
                for (String tech : p.getLeaderIDs()) {
                    allCards.remove(tech);
                }
            }
            for (int i = 0; i < size && allCards.size() > 0; i++) {
                cards.add(allCards.remove(0));
            }
        }
        if (type.equalsIgnoreCase("units")) {
            List<String> allCards = new ArrayList<>();
            Map<String, UnitModel> allUnits = Mapper.getUnits();
            for (String unitID : allUnits.keySet()) {
                UnitModel mod = allUnits.get(unitID);
                if (mod.getFaction().isPresent() && mod.getSource() == ComponentSource.twilights_fall) {
                    FactionModel faction = Mapper.getFaction(mod.getFaction().get());
                    if (faction != null && faction.getSource() != ComponentSource.twilights_fall) {
                        allCards.add(unitID);
                    }
                }
            }
            for (Player p : game.getRealPlayers()) {
                for (String tech : p.getUnitsOwned()) {
                    allCards.remove(tech);
                }
            }
            Collections.shuffle(allCards);
            for (int i = 0; i < size && allCards.size() > 0; i++) {
                cards.add(allCards.remove(0));
            }
        }
        game.removeStoredValue("savedSpliceCards");
        for (String card : cards) {
            if (game.getStoredValue("savedSpliceCards").isEmpty()) {
                game.setStoredValue("savedSpliceCards", card);
            } else {
                game.setStoredValue("savedSpliceCards", game.getStoredValue("savedSpliceCards") + "_" + card);
            }
        }
    }
}
