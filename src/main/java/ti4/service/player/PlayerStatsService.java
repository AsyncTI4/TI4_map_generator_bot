package ti4.service.player;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.omegaPhase.PriorityTrackHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.RiftSetModeService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class PlayerStatsService {

    public String getPlayersCurrentStatsText(Player player, Game game) {
        StringBuilder sb = new StringBuilder(player.getRepresentationNoPing() + "'s current stats:\n");

        sb.append("> VP: ").append(player.getTotalVictoryPoints());
        sb.append("      ").append(MiscEmojis.getTGorNomadCoinEmoji(game)).append(player.getTg());
        sb.append("      ").append(MiscEmojis.comm).append(player.getCommodities()).append("/").append(player.getCommoditiesTotal());
        sb.append("      ").append(ExploreEmojis.CFrag).append(player.getCrf());
        sb.append("   ").append(ExploreEmojis.IFrag).append(player.getIrf());
        sb.append("   ").append(ExploreEmojis.HFrag).append(player.getHrf());
        sb.append("   ").append(ExploreEmojis.UFrag).append(player.getUrf());

        sb.append("\n");

        sb.append("> Command Tokens: `").append(player.getCCRepresentation()).append("`\n");
        sb.append("> Strategy Cards: `").append(player.getSCs()).append("`\n");
        sb.append("> Unfollowed Strategy Cards: `").append(player.getUnfollowedSCs()).append("`\n");
        sb.append("> Debt: `").append(player.getDebtTokens()).append("`\n");
        sb.append("> Speaker: `").append(game.getSpeakerUserID().equals(player.getUserID())).append("`\n");
        sb.append("> Passed: `").append(player.isPassed()).append("`\n");
        sb.append("> Dummy: `").append(player.isDummy()).append("`\n");
        sb.append("> Raw Faction Emoji: `").append(player.getFactionEmoji()).append("`\n");
        sb.append("> Display Name: `").append(player.getDisplayName()).append("`\n");
        sb.append("> Stats Anchor: `").append(player.getPlayerStatsAnchorPosition()).append("`\n");

        Tile homeSystemTile = player.getHomeSystemTile();
        if (homeSystemTile != null) {
            sb.append("> Home System:  `").append(homeSystemTile.getPosition()).append("`\n");
        }

        sb.append("> Abilities: `").append(player.getAbilities()).append("`\n");
        sb.append("> Planets: `").append(player.getPlanets()).append("`\n");
        sb.append("> Technologies: `").append(player.getTechs()).append("`\n");
        sb.append("> Faction Technologies: `").append(player.getFactionTechs()).append("`\n");
        sb.append("> Fragments: `").append(player.getFragments()).append("`\n");
        sb.append("> Relics: `").append(player.getRelics()).append("`\n");
        sb.append("> Imperia Command Tokens: `").append(player.getMahactCC()).append("`\n");
        sb.append("> Leaders: `").append(player.getLeaderIDs()).append("`\n");
        sb.append("> Owned Promissory Notes: `").append(player.getPromissoryNotesOwned()).append("`\n");
        sb.append("> Player Area Promissory Notes: `").append(player.getPromissoryNotesInPlayArea()).append("`\n");
        sb.append("> Owned Units: `").append(player.getUnitsOwned()).append("`\n");
        sb.append("> Alliance Members: ").append(player.getAllianceMembers().replace(player.getFaction(), "")).append("\n");
        sb.append("> Followed SCs: `").append(player.getFollowedSCs().toString()).append("`\n");
        sb.append("> Expected Number of Hits: `").append((player.getExpectedHitsTimes10() / 10.0)).append("`\n");
        sb.append("> Actual Hits: `").append(player.getActualHits()).append("`\n");
        sb.append("> Total Unit Resource Value: ").append(MiscEmojis.resources).append("`").append(player.getTotalResourceValueOfUnits("both")).append("`\n");
        sb.append("> Total Unit Hit-point Value: ").append("🩷").append("`").append(player.getTotalHPValueOfUnits("both")).append("`\n");
        sb.append("> Total Unit Combat Expected Hits: ").append("💥").append("`").append(player.getTotalCombatValueOfUnits("both")).append("`\n");
        sb.append("> Total Unit Ability Expected Hits: ").append(TechEmojis.UnitUpgradeTech).append("`").append(player.getTotalUnitAbilityValueOfUnits()).append("`\n");
        sb.append("> Decal Set: `").append(player.getDecalName()).append("`\n");
        sb.append("\n");
        return sb.toString();
    }

    public static boolean pickSC(GenericInteractionCreateEvent event, Game game, Player player, OptionMapping optionSC) {
        if (optionSC == null) {
            return false;
        }
        int scNumber = optionSC.getAsInt();
        return secondHalfOfPickSC(event, game, player, scNumber);
    }

    public static boolean secondHalfOfPickSC(GenericInteractionCreateEvent event, Game game, Player player, int scNumber) {
        Map<Integer, Integer> scTradeGoods = game.getScTradeGoods();
        if (player.getColor() == null || "null".equals(player.getColor()) || player.getFaction() == null) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                "Can only pick strategy card if both faction and color have been picked.");
            return false;
        }
        if (!scTradeGoods.containsKey(scNumber)) {
            MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                "Strategy Card must be from possible ones in Game: " + scTradeGoods.keySet());
            return false;
        }

        Map<String, Player> players = game.getPlayers();
        for (Player playerStats : players.values()) {
            if (playerStats.getSCs().contains(scNumber)) {
                MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(),
                    Helper.getSCName(scNumber, game) + " is already picked.");
                return false;
            }
            if (scNumber == 9 && !RiftSetModeService.canPickSacrifice(player, game)) {
                return false;
            }
        }

        player.addSC(scNumber);
        if (game.isFowMode()) {
            String messageToSend = ColorEmojis.getColorEmojiWithName(player.getColor()) + " picked " + Helper.getSCName(scNumber, game);
            FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
        }

        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(scNumber).orElse(null);

        // WARNING IF PICKING TRADE WHEN PLAYER DOES NOT HAVE THEIR TRADE AGREEMENT
        if (scModel.usesAutomationForSCID("pok5trade") && !player.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
            String message = player.getRepresentationUnfogged() + " heads up, you just picked **Trade** but don't currently hold your _Trade Agreement_.";
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message);
        }

        Integer tgCount = scTradeGoods.get(scNumber);
        String msg = player.getRepresentationUnfogged() +
            " picked " + Helper.getSCRepresentation(game, scNumber) + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (tgCount != null && tgCount != 0) {
            int tg = player.getTg();
            tg += tgCount;
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getRepresentation() + " gained " + tgCount + " trade good" + (tgCount == 1 ? "" : "s")
                    + " from picking " + Helper.getSCName(scNumber, game) + ".");
            if (game.isFowMode()) {
                String messageToSend = ColorEmojis.getColorEmojiWithName(player.getColor()) + " gained " + tgCount
                    + " trade good" + (tgCount == 1 ? "" : "s") + " from picking " + Helper.getSCName(scNumber, game) + ".";
                FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
            }
            player.setTg(tg);
            CommanderUnlockCheckService.checkPlayer(player, "hacan");
            ButtonHelperAbilities.pillageCheck(player, game);
            if (scNumber == 2 && game.isRedTapeMode()) {
                for (int x = 0; x < tgCount; x++) {
                    ButtonHelper.offerRedTapeButtons(game, player);
                }
            }
        }
        return true;
    }

    public void setValue(
        SlashCommandInteractionEvent event, Game game, Player player, OptionMapping option,
        Consumer<Integer> consumer, Supplier<Integer> supplier
    ) {
        setValue(event, game, player, option.getName(), consumer, supplier, option.getAsString(), false);
    }

    public void setValue(
        SlashCommandInteractionEvent event, Game game, Player player, OptionMapping option,
        Consumer<Integer> consumer, Supplier<Integer> supplier, boolean suppressMessage
    ) {
        setValue(event, game, player, option.getName(), consumer, supplier, option.getAsString(),
            suppressMessage);
    }

    public void setValue(
        SlashCommandInteractionEvent event, Game game, Player player, String optionName,
        Consumer<Integer> consumer, Supplier<Integer> supplier, String value, boolean suppressMessage
    ) {
        try {
            boolean setValue = !value.startsWith("+") && !value.startsWith("-");
            String explanation = "";
            if (value.contains("?")) {
                explanation = value.substring(value.indexOf("?") + 1);
                value = value.substring(0, value.indexOf("?")).replace(" ", "");
            }

            int number = Integer.parseInt(value);
            int existingNumber = supplier.get();
            if (setValue) {
                consumer.accept(number);
                String messageToSend = getSetValueMessage(optionName, number, existingNumber,
                    explanation);
                if (!suppressMessage)
                    MessageHelper.sendMessageToEventChannel(event, messageToSend);
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
                }
            } else {
                int newNumber = existingNumber + number;
                newNumber = Math.max(newNumber, 0);
                consumer.accept(newNumber);
                String messageToSend = getChangeValueMessage(optionName, number, existingNumber,
                    newNumber, explanation);
                if (!suppressMessage)
                    MessageHelper.sendMessageToEventChannel(event, messageToSend);
                if (game.isFowMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(game, event, player, messageToSend);
                }
            }
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Could not parse number for: " + optionName);
        }
    }

    public static String getSetValueMessage(
        String optionName,
        Integer setToNumber, Integer existingNumber, String explanation
    ) {
        if (explanation == null || "".equalsIgnoreCase(explanation)) {
            return "> set **" + optionName + "** to **" + setToNumber + "**   _(was "
                + existingNumber + ", a change of " + (setToNumber - existingNumber)
                + ")_";
        } else {
            return "> set **" + optionName + "** to **" + setToNumber + "**   _(was "
                + existingNumber + ", a change of " + (setToNumber - existingNumber)
                + ")_ for the reason of: " + explanation;
        }

    }

    public static String getChangeValueMessage(
        String optionName,
        Integer changeNumber, Integer existingNumber, Integer newNumber, String explanation
    ) {
        String changeDescription = "changed";
        if (changeNumber > 0) {
            changeDescription = "increased";
        } else if (changeNumber < 0) {
            changeDescription = "decreased";
        }
        if (explanation == null || "".equalsIgnoreCase(explanation)) {
            return "> " + changeDescription + " **" + optionName + "** by " + changeNumber + "   _(was "
                + existingNumber + ", now **" + newNumber + "**)_";
        } else {
            return "> " + changeDescription + " **" + optionName + "** by " + changeNumber + "   _(was "
                + existingNumber + ", now **" + newNumber + "**)_ for the reason of: " + explanation;
        }
    }

    public static void setTotalCommodities(GenericInteractionCreateEvent event, Player player, Integer commoditiesTotalCount) {
        if (commoditiesTotalCount < 1 || commoditiesTotalCount > 10) {
            MessageHelper.sendMessageToEventChannel(event, "**Warning:** Total Commodities count seems like a wrong value:");
        }
        player.setCommoditiesTotal(commoditiesTotalCount);
        String message = ">  set **Total Commodities** to " + commoditiesTotalCount + MiscEmojis.comm;
        MessageHelper.sendMessageToEventChannel(event, message);
    }
}
