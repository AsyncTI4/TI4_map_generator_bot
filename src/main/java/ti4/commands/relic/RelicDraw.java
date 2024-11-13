package ti4.commands.relic;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.commands.cardspn.PlayPN;
import ti4.commands.cardsso.SOInfo;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;

public class RelicDraw extends RelicSubcommandData {

    public RelicDraw() {
        super(Constants.RELIC_DRAW, "Draw a relic");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        drawRelicAndNotify(player, event, game);
    }

    public static void drawWithAdvantage(Player player, GenericInteractionCreateEvent event, Game game, int advantage) {
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

    @ButtonHandler("drawRelicAtPosition_")
    public static void resolveDrawRelicAtPosition(Player player, ButtonInteractionEvent event, Game game, String buttonID) {
        int position = Integer.parseInt(buttonID.split("_")[1]);
        if (player.getPromissoryNotes().containsKey("dspnflor") && game.getPNOwner("dspnflor") != player) {
            PlayPN.resolvePNPlay("dspnflorChecked", player, game, event);
        }
        drawRelicAndNotify(player, event, game, position, true);
        event.getMessage().delete().queue();
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game game) {
        drawRelicAndNotify(player, event, game, 0, false);
    }

    public static void drawRelicAndNotify(Player player, GenericInteractionCreateEvent event, Game game, int position, boolean checked) {
        if (!checked && (player.hasAbility("data_leak") || (player.getPromissoryNotes().containsKey("dspnflor") && game.getPNOwner("dspnflor") != player))) {
            drawWithAdvantage(player, event, game, 2);
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
                SOInfo.sendSecretObjectiveInfo(game, player, event);
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
}
