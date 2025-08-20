package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

class ButtonHelperExplore {

    @ButtonHandler("exploreFront_")
    public static void exploreFront(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("exploreFront_", "");
        ButtonHelper.resolveFullFrontierExplore(game, player, game.getTileByPosition(pos), event);
        List<ActionRow> actionRow2 = new ArrayList<>();
        String exhaustedMessage = event.getMessage().getContentRaw();
        for (ActionRow row : event.getMessage().getActionRows()) {
            List<ItemComponent> buttonRow = row.getComponents();
            int buttonIndex = buttonRow.indexOf(event.getButton());
            if (buttonIndex > -1) {
                buttonRow.remove(buttonIndex);
            }
            if (!buttonRow.isEmpty()) {
                actionRow2.add(ActionRow.of(buttonRow));
            }
        }
        if ("".equalsIgnoreCase(exhaustedMessage)) {
            exhaustedMessage = "Explore";
        }
        if (!actionRow2.isEmpty()) {
            event.getMessage()
                    .editMessage(exhaustedMessage)
                    .setComponents(actionRow2)
                    .queue();
        } else {
            ButtonHelper.deleteMessage(event);
        }
    }

    @ButtonHandler("freelancersBuild_")
    public static void freelancersBuild(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("freelancersBuild_", "");
        List<Button> buttons;
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        if (tile == null) {
            tile = game.getTileByPosition(planet);
        }
        buttons = Helper.getPlaceUnitButtons(
                event, player, game, tile, "freelancers", "placeOneNDone_dontskipfreelancers");
        String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("purge_Frags_")
    public static void purgeFrags(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String typeNAmount = buttonID.replace("purge_Frags_", "");
        String type = typeNAmount.split("_")[0];
        int count = Integer.parseInt(typeNAmount.split("_")[1]);
        List<String> fragmentsToPurge = new ArrayList<>();
        List<String> playerFragments = player.getFragments();
        for (String fragid : playerFragments) {
            if (fragid.contains(type.toLowerCase())) {
                fragmentsToPurge.add(fragid);
            }
        }
        if (fragmentsToPurge.size() == count) {
            ButtonHelper.deleteTheOneButton(event);
        }
        while (fragmentsToPurge.size() > count) {
            fragmentsToPurge.removeFirst();
        }

        StringBuilder message = new StringBuilder(player.getRepresentation() + " purged");
        if (fragmentsToPurge.size() == 1) {
            String fragid = fragmentsToPurge.getFirst();
            player.removeFragment(fragid);
            game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
            switch (fragid) {
                case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9" ->
                    message.append(" a " + ExploreEmojis.CFrag + "cultural");
                case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7" ->
                    message.append(" a " + ExploreEmojis.HFrag + "hazardous");
                case "irf1", "irf2", "irf3", "irf4", "irf5" ->
                    message.append(" an " + ExploreEmojis.IFrag + "industrial");
                case "urf1", "urf2", "urf3" -> message.append(" an " + ExploreEmojis.UFrag + "unknown");
                default -> message.append(" ").append(fragid);
            }
            message.append(" relic fragment.");
        } else {
            for (String fragid : fragmentsToPurge) {
                player.removeFragment(fragid);
                game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
                switch (fragid) {
                    case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9" ->
                        message.append(ExploreEmojis.CFrag);
                    case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7" -> message.append(ExploreEmojis.HFrag);
                    case "irf1", "irf2", "irf3", "irf4", "irf5" -> message.append(ExploreEmojis.IFrag);
                    case "urf1", "urf2", "urf3" -> message.append(ExploreEmojis.UFrag);
                    default -> message.append(" ").append(fragid);
                }
            }
            message.append(" relic fragments.");
        }
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "lanefir");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
        if (!game.isFowMode() && event.getMessageChannel() instanceof ThreadChannel) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        }

        if (player.hasTech("dslaner")) {
            player.setAtsCount(player.getAtsCount() + 1);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentation() + " put 1 commodity on _ATS Armaments_.");
        }
    }
}
