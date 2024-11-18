package ti4.helpers;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.RelicModel;

@UtilityClass
public class RelicHelper {

    public static void drawWithAdvantage(Player player, Game game, int advantage) {
        List<Button> buttons = new ArrayList<>();
        List<String> relics = game.getAllRelics();
        StringBuilder info = new StringBuilder();
        for (int x = 0; x < advantage && x < relics.size(); x++) {
            RelicModel relicData = Mapper.getRelic(relics.get(x));
            buttons.add(Buttons.green("drawRelicAtPosition_" + x, relicData.getName()));
            info.append(relicData.getName()).append(": ").append(relicData.getText()).append("\n");
        }
        String msg = player.getRepresentationUnfogged() + " choose the relic that you want. The relic text is reproduced for your conveinenance";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), info.toString());
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game game) {
        drawRelicAndNotify(player, event, game, 0, false);
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game game, int position, boolean checked) {
        if (!checked && (player.hasAbility("data_leak") || (player.getPromissoryNotes().containsKey("dspnflor") && game.getPNOwner("dspnflor") != player))) {
            drawWithAdvantage(player, game, 2);
            return;
        }
        if (player.hasAbility("a_new_edifice")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + "Due to A New Edifice Ability, you get to explore 3 planets rather than get a relic. Reminder that they should be different planets. ");
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + "Explore planet #1 ", buttons);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + "Explore planet #2 ", buttons);
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() + "Explore planet #3 ", buttons);
            return;
        }

        String relicID = game.drawRelic(position);
        if (relicID.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Relic deck is empty");
            return;
        }
        relicID = relicID.replace("extra1", "");
        relicID = relicID.replace("extra2", "");
        player.addRelic(relicID);
        RelicModel relicModel = Mapper.getRelic(relicID);

        String message = player.getRepresentation() + " drew a Relic:";
        if (game.isFowMode()) {
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, message);
        }
        MessageHelper.sendMessageToChannelWithEmbed(player.getCorrectChannel(), message, relicModel.getRepresentationEmbed(false, true));
        resolveRelicEffects(event, game, player, relicID);

        if (checked) game.shuffleRelics();
    }

    public static void resolveRelicEffects(GenericInteractionCreateEvent event, Game game, Player player, String relicID) {
        StringBuilder helpMessage = new StringBuilder();
        //Append helpful commands after relic draws and resolve effects:
        switch (relicID) {
            case "nanoforge" -> helpMessage.append("Run the following commands to use Nanoforge:\n")
                .append("     `/explore relic_purge relic: nanoforge`\n")
                .append("     `/add_token token:nanoforge tile_name:{TILE} planet_name:{PLANET}`");
            case "obsidian" -> {
                game.drawSecretObjective(player.getUserID());

                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, "Drew An SO");
                }

                helpMessage.append("\nAn SO has been automatically drawn.");
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    helpMessage.append(" Drew a second SO due to Plausible Deniability.");
                }
                SecretObjectiveHelper.sendSecretObjectiveInfo(game, player, event);
            }
            case "shard" -> {
                Integer poIndex = game.addCustomPO("Shard of the Throne", 1);
                game.scorePublicObjective(player.getUserID(), poIndex);
                helpMessage.append("Custom PO 'Shard of the Throne' has been added.\n")
                    .append(player.getRepresentation()).append(" scored 'Shard of the Throne'");
            }
            case "absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3" -> {
                int absolShardNum = Integer.parseInt(StringUtils.right(relicID, 1));
                String customPOName = "Shard of the Throne (" + absolShardNum + ")";
                Integer poIndex = game.addCustomPO(customPOName, 1);
                game.scorePublicObjective(player.getUserID(), poIndex);
                helpMessage.append("Custom PO '").append(customPOName).append("' has been added.\n")
                    .append(player.getRepresentation()).append(" scored '").append(customPOName).append("'");
            }
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), helpMessage.toString());
        Helper.checkEndGame(game, player);
    }

    public static void sendRelicInfo(Player player) {
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
            player.getCardsInfoThread(),
            null,
            getRelicEmbeds(player),
            getRelicButtons());
    }

    private static List<MessageEmbed> getRelicEmbeds(Player player) {
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        for (String relicID : player.getRelics()) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            if (relicModel != null) {
                MessageEmbed representationEmbed = relicModel.getRepresentationEmbed();
                messageEmbeds.add(representationEmbed);
            }
        }
        return messageEmbeds;

    }

    private static List<Button> getRelicButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.REFRESH_RELIC_INFO);
        return buttons;
    }

    public void sendFrags(GenericInteractionCreateEvent event, Player sender, Player receiver, String trait, int count, Game game) {
        List<String> fragments = new ArrayList<>();
        for (String cardID : sender.getFragments()) {
            ExploreModel card = Mapper.getExplore(cardID);
            if (card.getType().equalsIgnoreCase(trait)) {
                fragments.add(cardID);
            }
        }

        if (fragments.size() >= count) {
            for (int i = 0; i < count; i++) {
                String fragID = fragments.get(i);
                sender.removeFragment(fragID);
                receiver.addFragment(fragID);
            }
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Not enough fragments of the specified trait");
            return;
        }

        String emojiName = switch (trait) {
            case "cultural" -> "CFrag";
            case "hazardous" -> "HFrag";
            case "industrial" -> "IFrag";
            case "frontier" -> "UFrag";
            default -> "";
        };

        String p1 = sender.getRepresentation();
        String p2 = receiver.getRepresentation();
        String fragString = count + " " + trait + " " + Emojis.getEmojiFromDiscord(emojiName) + " relic fragments";
        String message = p1 + " sent " + fragString + " to " + p2;
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(receiver.getCorrectChannel(), message);
        }
        CommanderUnlockCheck.checkPlayer(receiver, "kollecc", "bentor");

        if (game.isFowMode()) {
            String fail = "User for faction not found. Report to ADMIN";
            String success = "The other player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(receiver, game, event, message, fail, success);

            // Add extra message for transaction visibility
            FoWHelper.pingPlayersTransaction(game, event, sender, receiver, fragString, null);
        }
        TransactionHelper.checkTransactionLegality(game, sender, receiver);
        CommanderUnlockCheck.checkPlayer(receiver, "kollecc");
    }

    public static void showRemaining(GenericInteractionCreateEvent event, boolean over, Game game, Player player) {
        List<String> allRelics = new ArrayList<>(game.getAllRelics());

        Integer deckCount = allRelics.size();
        Double deckDrawChance = deckCount == 0 ? 0.0 : 1.0 / deckCount;
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMaximumFractionDigits(1);
        StringBuilder text;
        if (allRelics.isEmpty()) {
            text = new StringBuilder("**RELIC DECK IS EMPTY**");
        } else {
            text = new StringBuilder(Emojis.Relic).append(" **RELICS REMAINING IN DECK** (").append(deckCount).append(") _").append(formatPercent.format(deckDrawChance)).append("_\n");
            Collections.sort(allRelics);
            for (String relicId : allRelics) {
                String relicName = Mapper.getRelic(relicId).getName();
                text.append("- ").append(relicName).append("\n");
            }
        }

        if (player != null && "action".equalsIgnoreCase(game.getPhaseOfGame()) && !over && game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "It is foggy outside, please wait until status/agenda to do this command, or override the fog.");
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), text.toString());
        }
    }
}
