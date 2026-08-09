package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class ButtonHelperExplore {

    @ButtonHandler("exploreFront_")
    public static void exploreFront(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("exploreFront_", "");
        ButtonHelper.resolveFullFrontierExplore(game, player, game.getTileByPosition(pos), event);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
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
        String trait =
                switch (type.toLowerCase()) {
                    case "crf" -> "cultural";
                    case "hrf" -> "hazardous";
                    case "irf" -> "industrial";
                    case "urf" -> "frontier";
                    default -> "";
                };
        boolean prioritizeSupermassive = RelicHelper.hasPurgedRelicFragmentOfType(game, trait);
        List<String> fragmentsToPurge = new ArrayList<>();
        for (String fragId : player.getFragments()) {
            ExploreModel fragment = Mapper.getExplore(fragId);
            if (fragment != null && trait.equalsIgnoreCase(fragment.getType())) {
                if (prioritizeSupermassive && fragId.startsWith("supermassive")) {
                    fragmentsToPurge.addFirst(fragId);
                } else {
                    fragmentsToPurge.add(fragId);
                }
            }
        }
        if (fragmentsToPurge.size() == count) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
        while (fragmentsToPurge.size() > count) {
            fragmentsToPurge.removeLast();
        }

        StringBuilder message = new StringBuilder(player.getRepresentation() + " purged ");
        if (fragmentsToPurge.size() == 1) {
            String fragId = fragmentsToPurge.getFirst();
            player.removeFragment(fragId);
            game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
            switch (fragId) {
                case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9", "supermassivecultural" ->
                    message.append(" a " + (fragId.contains("supermassive") ? "supermassive " : "")
                            + ExploreEmojis.CFrag + "cultural");
                case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7", "supermassivehazardous" ->
                    message.append(" a " + (fragId.contains("supermassive") ? "supermassive " : "")
                            + ExploreEmojis.HFrag + "hazardous");
                case "irf1", "irf2", "irf3", "irf4", "irf5", "supermassiveindustrial" ->
                    message.append(" an " + (fragId.contains("supermassive") ? "supermassive " : "")
                            + ExploreEmojis.IFrag + "industrial");
                case "urf1", "urf2", "urf3", "supermassiveunknown" ->
                    message.append(" an " + (fragId.contains("supermassive") ? "supermassive " : "")
                            + ExploreEmojis.UFrag + "unknown");
                default -> message.append(' ').append(fragId);
            }
            message.append(" relic fragment.");
        } else {
            for (String fragId : fragmentsToPurge) {
                player.removeFragment(fragId);
                game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
                switch (fragId) {
                    case "crf1",
                            "crf2",
                            "crf3",
                            "crf4",
                            "crf5",
                            "crf6",
                            "crf7",
                            "crf8",
                            "crf9",
                            "supermassivecultural" -> message.append(ExploreEmojis.CFrag);
                    case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7", "supermassivehazardous" ->
                        message.append(ExploreEmojis.HFrag);
                    case "irf1", "irf2", "irf3", "irf4", "irf5", "supermassiveindustrial" ->
                        message.append(ExploreEmojis.IFrag);
                    case "urf1", "urf2", "urf3", "supermassiveunknown" -> message.append(ExploreEmojis.UFrag);
                    default -> message.append(' ').append(fragId);
                }
            }
            message.append(" relic fragments.");
        }
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "lanefir");
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
        if (!game.isFowMode() && event.getMessageChannel() instanceof ThreadChannel) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        }

        if (player.hasTech("dslaner") && !game.isTwilightsFallMode()) {
            player.setAtsCount(player.getAtsCount() + 1);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentation() + " put 1 commodity on _ATS Armaments_.");
        }
    }
}
