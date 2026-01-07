package ti4.helpers.thundersedge;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.helpers.BreakthroughHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.BreakthroughModel;
import ti4.service.breakthrough.AlRaithService;
import ti4.service.breakthrough.DataSkimmerService;
import ti4.service.breakthrough.SowingReapingService;
import ti4.service.breakthrough.StellarGenesisService;
import ti4.service.breakthrough.VoidTetherService;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.map.FractureService;

public class BreakthroughCommandHelper {

    public static List<String> getBreakthroughsFromEvent(SlashCommandInteractionEvent event, Player player) {
        return getBreakthroughsFromEvent(event, player, false);
    }

    public static List<String> getBreakthroughsFromEvent(
            SlashCommandInteractionEvent event, Player player, boolean limitOne) {
        String btID = event.getOption(Constants.BREAKTHROUGH, null, OptionMapping::getAsString);
        if (btID == null || btID.isEmpty()) {
            if (player == null) return List.of();
            if (player.getBreakthroughIDs().size() == 1) return player.getBreakthroughIDs();
        } else if (player == null) {
            for (BreakthroughModel model : Mapper.getBreakthroughs().values()) {
                if (btID.equals(model.getAlias()) || btID.startsWith(model.getName())) {
                    return List.of(btID);
                }
            }
            return List.of();
        } else if (player.hasBreakthrough(btID)) {
            return List.of(btID);
        } else if (!limitOne && "all".equalsIgnoreCase(btID)) {
            return player.getBreakthroughIDs();
        } else {
            for (String id : player.getBreakthroughIDs()) {
                BreakthroughModel model = player.getBreakthroughModel(btID);
                if (model != null && model.getName().equalsIgnoreCase(btID.trim())) {
                    return List.of(id);
                }
            }
        }

        if (player.getBreakthroughIDs().size() == 1) return player.getBreakthroughIDs();
        return List.of();
    }

    private static void withBreakthrough(Player player, String btID, Consumer<BreakthroughModel> action) {
        BreakthroughModel bt = player.getBreakthroughModel(btID);
        if (bt == null) {
            BreakthroughModel model = Mapper.getBreakthrough(btID);
            String message = "Player does not have a breakthrough.";
            if (model == null) {
                message = "Breakthrough `" + btID + "` does not exist.";
            } else if (!player.getBreakthroughIDs().isEmpty()) {
                message = player.getRepresentationNoPing() + " does not have _" + model.getName() + "_.";
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        } else {
            action.accept(bt);
        }
    }

    private static void withEachBreakthrough(Player player, List<String> btIDs, Consumer<BreakthroughModel> action) {
        if (btIDs.isEmpty()) {
            String message = "Player does not have a breakthrough.";
            if (!player.getBreakthroughIDs().isEmpty())
                message = player.getRepresentationNoPing() + " does not have that breakthrough.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        } else {
            for (String bt : btIDs) {
                BreakthroughModel model = player.getBreakthroughModel(bt);
                if (model == null) continue;
                action.accept(model);
            }
        }
    }

    private static void withAllBreakthroughs(Player player, Consumer<List<BreakthroughModel>> action) {
        List<BreakthroughModel> models = player.getBreakthroughModels();
        if (models.isEmpty()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Player does not have a breakthrough.");
        } else {
            action.accept(models);
        }
    }

    public static void sendBreakthroughInfo(Game game, Player player) {
        withAllBreakthroughs(player, bts -> {
            List<MessageEmbed> embeds =
                    bts.stream().map(BreakthroughModel::getRepresentationEmbed).toList();
            MessageHelper.sendMessageEmbedsToCardsInfoThread(player, "", embeds);
        });
    }

    public static void sendBreakthroughInfo(
            GenericInteractionCreateEvent event, Game game, Player player, Player target) {
        if (target == null) target = player;
        withAllBreakthroughs(target, bts -> {
            List<MessageEmbed> embeds =
                    bts.stream().map(BreakthroughModel::getRepresentationEmbed).toList();
            String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
            MessageHelper.sendMessageEmbedsToCardsInfoThread(player, headerText, embeds);
        });
    }

    public static void exhaustBreakthroughs(Player player, List<String> btIDs) {
        withEachBreakthrough(player, btIDs, bt -> {
            player.getBreakthroughExhausted().put(bt.getAlias(), true);
            String message = player.getRepresentation() + " exhausted their _" + bt.getName() + "_ breakthrough.";
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(), message, bt.getRepresentationEmbed());
        });
    }

    public static void exhaustBreakthrough(Player player, String... btIDs) {
        exhaustBreakthroughs(player, Arrays.asList(btIDs));
    }

    public static void readyBreakthroughs(Player player, List<String> btIDs) {
        withEachBreakthrough(player, btIDs, bt -> {
            player.getBreakthroughExhausted().put(bt.getAlias(), false);
            String message = player.getRepresentation() + " readied their _" + bt.getName() + "_ breakthrough.";
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(), message, bt.getRepresentationEmbed());
        });
    }

    public static void readyBreakthrough(Player player, String... btIDs) {
        readyBreakthroughs(player, Arrays.asList(btIDs));
    }

    public static void unlockAllBreakthroughs(Game game, Player player) {
        List<String> lockedBtIDs = player.getBreakthroughUnlocked().entrySet().stream()
                .filter(e -> !e.getValue())
                .map(e -> e.getKey())
                .toList();
        if (!lockedBtIDs.isEmpty()) {
            unlockBreakthroughs(game, player, lockedBtIDs);
        }
    }

    public static void unlockBreakthroughs(Game game, Player player, List<String> btIDs) {
        withEachBreakthrough(player, btIDs, bt -> {
            String btID = bt.getID();
            if (player.isBreakthroughUnlocked(btID)) return;

            player.setBreakthroughUnlocked(btID, true);
            player.setBreakthroughExhausted(btID, false);
            String message = player.getRepresentation() + " unlocked their _" + bt.getName() + "_ breakthrough.";
            List<MessageEmbed> embeds = Collections.singletonList(bt.getRepresentationEmbed());
            MessageHelper.sendMessageToChannelWithEmbeds(player.getCorrectChannel(), message, embeds);
            if ("yinbt".equalsIgnoreCase(bt.getID())) {
                BreakthroughHelper.resolveYinBreakthroughAbility(player.getGame(), player);
            }
            if ("khraskbt".equalsIgnoreCase(bt.getID())) {
                player.addPlanet("grove");
                Planet grove = game.getPlanetsInfo().get("grove");
                if (grove != null) grove.updateGroveStats(player);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(), "Added the Grove \"planet\" card to your play area.");
            }
            if ("mentakbt".equalsIgnoreCase(bt.getID())) {
                if (player.hasTech("cr2")) {
                    player.addOwnedUnitByID("mentak_cruiser3");
                    player.removeOwnedUnitByID("cruiser2");
                }
            }
            if ("celdauribt".equalsIgnoreCase(bt.getID())) {
                player.addOwnedUnitByID("celdauri_celagrom");
                String output = player.getRepresentation()
                        + ", please choose the system in which you wish to place the _Celagrom_.";
                List<Button> buttons =
                        Helper.getTileWithShipsPlaceUnitButtons(player, game, "celagrom", "placeOneNDone_skipbuild");
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), output, buttons);
            }
            if ("rohdhnabt".equalsIgnoreCase(bt.getID())) {
                if (player.hasTech("dsrohdws")) {
                    player.addOwnedUnitByID("rohdhna_warsun3");
                    player.removeOwnedUnitByID("rohdhna_warsun2");
                }
            }
            if ("kortalbt".equalsIgnoreCase(bt.getID())) {
                if (player.hasTech("dn2")) {
                    player.addOwnedUnitByID("tribune3");
                    player.removeOwnedUnitByID("dreadnought2");
                }
            }

            if ("cabalbt".equalsIgnoreCase(bt.getID())) {
                if (btIDs.size() == 1) {
                    // If there are other BTs to potentially roll, don't automatically spawn
                    if (!FractureService.isFractureInPlay(game)) {
                        String msg = player.getRepresentation(false, false)
                                + " has gained _Al'Raith Ix Ianovar_, and so The Fracture enters play automatically!"
                                + " Ingress tokens will be placed in their position on the map, if there were no choices to be made.";
                        FractureService.spawnFracture(null, game);
                        FractureService.spawnIngressTokens(null, game, player, bt.getID());
                        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
                    }
                    AlRaithService.serveBeginCabalBreakthroughButtons(null, game, player);
                }
            }
            if ("firmamentbt".equalsIgnoreCase(bt.getID())) {
                SowingReapingService.sendTheSowingButtons(game);
            }

            if (!FractureService.isFractureInPlay(game) && !game.isNoFractureMode())
                serveRollFractureButtons(game, player, btID);
            if ("muaatbt".equals(bt.getAlias())) StellarGenesisService.serveAvernusButtons(game, player);
            if ("keleresbt".equals(bt.getAlias())) player.gainCustodiaVigilia();
        });
    }

    public static void unlockBreakthrough(Game game, Player player, String... btIDs) {
        unlockBreakthroughs(game, player, Arrays.asList(btIDs));
    }

    public static void serveRollFractureButtons(Game game, Player player, String btID) {
        String id = player.finChecker() + "rollFracture_" + btID;
        Button rollFracture = Buttons.green(id, "Roll For The Fracture", MiscEmojis.RollDice);
        String message = "It looks like The Fracture isn't in play yet. Use the button to roll for The Fracture!"
                + "\n If you roll a " + DiceEmojis.d10blue_1 + " or a"
                + DiceEmojis.d10blue_0 + ", The Fracture will appear.";
        if ("cabalbt".equals(btID)) {
            rollFracture = rollFracture.withLabel("Spawn Fracture").withEmoji(FactionEmojis.Cabal.asEmoji());
            message =
                    "You can roll for other breakthroughs first and then spawn The Fracture with _Al'Raith Ix Ianovar_.";
        }
        MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), message, rollFracture);
    }

    public static void lockBreakthroughs(Player player, List<String> btIDs) {
        withEachBreakthrough(player, btIDs, bt -> {
            String btID = bt.getID();
            if (!player.isBreakthroughUnlocked(btID)) return;

            player.setBreakthroughUnlocked(btID, false);
            String message = player.getRepresentation() + " locked their _" + bt.getName() + "_ breakthrough.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        });
    }

    public static void lockBreakthrough(Player player, String... btIDs) {
        lockBreakthroughs(player, Arrays.asList(btIDs));
    }

    public static void activateBreakthroughs(GenericInteractionCreateEvent event, Player player, List<String> btIDs) {
        withEachBreakthrough(player, btIDs, bt -> {
            String btID = bt.getID();
            List<String> activatable = List.of("naazbt");
            if (activatable.contains(btID)) {
                if (!player.isBreakthroughActive(btID)) {
                    player.setBreakthroughActive(btID, true);
                    String message =
                            player.getRepresentation() + " activated their _" + bt.getName() + "_ breakthrough.";
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                    if ("naazbt".equalsIgnoreCase(bt.getAlias())) {
                        player.addOwnedUnitByID("naaz_voltron");
                        player.removeOwnedUnitByID("naaz_mech");
                        player.removeOwnedUnitByID("naaz_mech_space");
                    }
                }
            } else {
                switch (btID) {
                    case "ralnelbt" -> DataSkimmerService.fixDataSkimmer(player.getGame(), player);
                    case "empyreanbt" -> VoidTetherService.fixVoidTether(player.getGame(), player);
                    default -> {
                        String msg = player.getRepresentation() + " could not activate their _" + bt.getName()
                                + "_ breakthrough.";
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                    }
                }
            }
        });
    }

    public static void activateBreakthrough(GenericInteractionCreateEvent event, Player player, String... btIDs) {
        activateBreakthroughs(event, player, Arrays.asList(btIDs));
    }

    public static void deactivateBreakthroughs(Player player, List<String> btIDs) {
        withEachBreakthrough(player, btIDs, bt -> {
            String btID = bt.getID();
            if (player.isBreakthroughActive(btID)) {
                player.setBreakthroughActive(btID, false);
                String message =
                        player.getRepresentation() + " de-activated their _" + bt.getName() + "_ breakthrough.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                if ("naazbt".equalsIgnoreCase(bt.getAlias())) {
                    player.removeOwnedUnitByID("naaz_voltron");
                    player.addOwnedUnitByID("naaz_mech");
                    player.addOwnedUnitByID("naaz_mech_space");
                }
            }
        });
    }

    public static void deactivateBreakthrough(Player player, String... btIDs) {
        deactivateBreakthroughs(player, Arrays.asList(btIDs));
    }

    public static void setBreakthroughTGs(Player player, int newTgs, String btID) {
        if (newTgs < 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "You cannot have negative trade goods on your breakthrough.");
        } else if (!player.isBreakthroughUnlocked(btID)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "You do not have your breakthrough unlocked, so you cannot add trade goods to it.");
        } else {
            int initial = player.getBreakthroughTGs(btID);
            player.getBreakthroughTGs().put(btID, newTgs);
            String msg = player.getRepresentation() + " set the trade goods on their breakthrough to " + newTgs + " ("
                    + initial + "->" + newTgs + ").";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        }
    }

    public static void updateBreakthroughTradeGoods(Player player, String option, String btID) {
        withBreakthrough(player, btID, bt -> {
            int newTgs = readPlusMinus(player.getBreakthroughTGs(btID), option);
            setBreakthroughTGs(player, newTgs, btID);
        });
    }

    private static int readPlusMinus(int initial, String option) {
        Pattern pattern = Pattern.compile("(?<pm>([\\+\\-]?))(?<amt>(\\d+))");
        Matcher matcher = pattern.matcher(option);
        if (matcher.matches()) {
            String pm = matcher.group("pm");
            int amt = Integer.parseInt(matcher.group("amt"));
            if (pm != null && !pm.isBlank()) {
                if ("-".equals(pm)) return initial - amt;
                return initial + amt;
            }
            return amt;
        }
        return initial;
    }
}
