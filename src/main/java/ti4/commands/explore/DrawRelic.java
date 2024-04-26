package ti4.commands.explore;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.cardsso.SOInfo;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

public class DrawRelic extends GenericRelicAction {

    public DrawRelic() {
        super(Constants.RELIC_DRAW, "Draw a relic");
    }

    @Override
    public void doAction(Player player, SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        drawRelicAndNotify(player, event, activeGame);
    }

    public static void drawWithAdvantage(Player player, GenericInteractionCreateEvent event, Game activeGame, int advantage) {
        List<Button> buttons = new ArrayList<>();
        List<String> relics = activeGame.getAllRelics();
        StringBuilder info = new StringBuilder();
        for (int x = 0; x < advantage && x < relics.size(); x++) {
            RelicModel relicData = Mapper.getRelic(relics.get(x));
            buttons.add(Button.success("drawRelicAtPosition_" + x, relicData.getName()));
            info.append(relicData.getName()).append(": ").append(relicData.getText()).append("\n");
        }
        String msg = player.getRepresentation(true, true) + " choose the relic that you want. The relic text is reproduced for your conveinenance";
        MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), msg, buttons);
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), info.toString());
    }

    public static void resolveDrawRelicAtPosition(Player player, ButtonInteractionEvent event, Game activeGame, String buttonID) {
        int position = Integer.parseInt(buttonID.split("_")[1]);
        if (player.getPromissoryNotes().containsKey("dspnflor") && activeGame.getPNOwner("dspnflor") != player) {
            ButtonHelper.resolvePNPlay("dspnflorChecked", player, activeGame, event);
        }
        drawRelicAndNotify(player, event, activeGame, position, true);
        event.getMessage().delete().queue();
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game activeGame) {
        drawRelicAndNotify(player, event, activeGame, 0, false);
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game activeGame, int position, boolean checked) {
        if (!checked && (player.hasAbility("data_leak") || (player.getPromissoryNotes().containsKey("dspnflor") && activeGame.getPNOwner("dspnflor") != player))) {
            drawWithAdvantage(player, event, activeGame, 2);
            return;
        }
        if (player.hasAbility("a_new_edifice")) {
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + "Due to A New Edifice Ability, you get to explore 3 planets rather than get a relic. Reminder that they should be different planets. ");
            List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, activeGame);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + "Explore planet #1 ", buttons);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + "Explore planet #2 ", buttons);
            MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), player.getRepresentation() + "Explore planet #3 ", buttons);
            return;
        }

        String relicID = activeGame.drawRelic(position);
        if (relicID.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Relic deck is empty");
            return;
        }
        relicID = relicID.replace("extra1", "");
        relicID = relicID.replace("extra2", "");
        player.addRelic(relicID);
        RelicModel relicData = Mapper.getRelic(relicID);
        StringBuilder message = new StringBuilder();
        message.append(player.getRepresentation()).append(" drew a Relic:\n").append(Emojis.Relic).append(" __**").append(relicData.getName()).append("**__\n> ").append(relicData.getText())
            .append("\n");

        //Append helpful commands after relic draws and resolve effects:
        switch (relicID) {
            case "nanoforge" -> message.append("Run the following commands to use Nanoforge:\n")
                .append("     `/explore relic_purge relic: nanoforge`\n")
                .append("     `/add_token token:nanoforge tile_name:{TILE} planet_name:{PLANET}`");
            case "obsidian" -> {
                activeGame.drawSecretObjective(player.getUserID());

                if (activeGame.isFoWMode()) {
                    FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, "Drew SO");
                }

                message.append("\nAn SO has been automatically drawn");
                if (player.hasAbility("plausible_deniability")) {
                    activeGame.drawSecretObjective(player.getUserID());
                    message.append(". Drew a second SO due to plausible deniability");
                }
                SOInfo.sendSecretObjectiveInfo(activeGame, player, event);
            }
            case "shard" -> {
                Integer poIndex = activeGame.addCustomPO("Shard of the Throne", 1);
                activeGame.scorePublicObjective(player.getUserID(), poIndex);
                message.append("Custom PO 'Shard of the Throne' has been added.\n")
                    .append(player.getRepresentation()).append(" scored 'Shard of the Throne'");
            }
            case "absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3" -> {
                int absolShardNum = Integer.parseInt(StringUtils.right(relicID, 1));
                String customPOName = "Shard of the Throne (" + absolShardNum + ")";
                Integer poIndex = activeGame.addCustomPO(customPOName, 1);
                activeGame.scorePublicObjective(player.getUserID(), poIndex);
                message.append("Custom PO '").append(customPOName).append("' has been added.\n")
                    .append(player.getRepresentation()).append(" scored '").append(customPOName).append("'");
            }
        }
        if (activeGame.isFoWMode()) {
            FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, message.toString());
        }
        MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), message.toString());
        if (checked) {
            activeGame.shuffleRelics();
        }
        Helper.checkEndGame(activeGame, player);
    }
}
