package ti4.commands.combat;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.generator.GenerateTile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CombatRollType;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class StartCombatThread extends CombatSubcommandData {

    public StartCombatThread() {
        super(Constants.START_COMBAT, "Start a new combat thread for a given tile.");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile to move units from").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
    }

    public static String combatThreadName(Game activeGame, Player player1, Player player2, Tile tile) {
        StringBuilder sb = new StringBuilder();
        sb.append(activeGame.getName()).append("-round-").append(activeGame.getRound()).append("-system-").append(tile.getPosition()).append("-");
        if (activeGame.isFoWMode()) {
            sb.append(player1.getColor());
            if (player2 != null) sb.append("-vs-").append(player2.getColor()).append("-private");
        } else {
            sb.append(player1.getFaction());
            if (player2 != null) sb.append("-vs-").append(player2.getFaction());
        }
        return sb.toString();
    }

    public static void makeACombatThread(Game activeGame, MessageChannel channel, Player p1, Player p2, String threadName, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround) {
        TextChannel textChannel = (TextChannel) channel;
        Helper.checkThreadLimitAndArchive(event.getGuild());
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                initializeCombatThread(threadChannel_, activeGame, p1, p2, tile, event, spaceOrGround);
                return;
            }
        }
        List<Player> playersWithPds2;
        if (activeGame.isFoWMode() || "ground".equalsIgnoreCase(spaceOrGround)) {
            playersWithPds2 = new ArrayList<>();
        } else {
            playersWithPds2 = ButtonHelper.tileHasPDS2Cover(p1, activeGame, tile.getPosition());
        }
        int context = 0;
        if (playersWithPds2.size() > 0) {
            context = 1;
        }
        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event, p1);
        MessageCreateBuilder baseMessageObject = new MessageCreateBuilder().addContent("Resolve combat");
        channel.sendMessage(baseMessageObject.build()).queue(message -> {
            ThreadChannelAction threadChannel = textChannel.createThreadChannel(threadName, message.getId());
            if (activeGame.isFoWMode()) {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
            } else {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
            }
            threadChannel.queue(tc -> initializeCombatThread(tc, activeGame, p1, p2, tile, event, spaceOrGround, systemWithContext));
        });

    }

    private static void initializeCombatThread(ThreadChannel tc, Game activeGame, Player p1, Player p2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround) {
        initializeCombatThread(tc, activeGame, p1, p2, tile, event, spaceOrGround, null);
    }

    private static void initializeCombatThread(ThreadChannel tc, Game activeGame, Player p1, Player p2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, FileUpload file) {
        StringBuilder message = new StringBuilder();
        if (activeGame.isFoWMode()) {
            message.append(p1.getRepresentation(true, true));
        } else {
            message.append(p1.getRepresentation(true, true));
            message.append(p2.getRepresentation());
        }

        message.append(" Please resolve the interaction here. ");
        if ("ground".equalsIgnoreCase(spaceOrGround)) {
            message.append("Steps for Invasion:").append("\n");
            message.append("> 1. Start of invasion abilities (Tekklar, Blitz, Bunker, etc.)").append("\n");
            message.append("> 2. Bombardment").append("\n");
            message.append("> 3. Commit Ground Forces").append("\n");
            message.append("> 4. After commit window (Parley, Ghost Squad, etc.)").append("\n");
            message.append("> 5. Start of Combat (morale boost, etc.)").append("\n");
            message.append("> 6. Roll Dice!").append("\n");
        } else {
            message.append("Steps for Space Combat:").append("\n");
            message.append("> 1. End of movement abilities (Foresight, Stymie, etc.)").append("\n");
            message.append("> 2. Firing of PDS").append("\n");
            message.append("> 3. Start of Combat (Skilled retreat, Morale boost, etc.)").append("\n");
            message.append("> 4. Anti-Fighter Barrage").append("\n");
            message.append("> 5. Declare Retreats (including rout)").append("\n");
            message.append("> 6. Roll Dice!").append("\n");
        }

        MessageHelper.sendMessageToChannel(tc, message.toString());
        List<Player> playersWithPds2;
        if (activeGame.isFoWMode() || "ground".equalsIgnoreCase(spaceOrGround)) {
            playersWithPds2 = new ArrayList<>();
        } else {
            playersWithPds2 = ButtonHelper.tileHasPDS2Cover(p1, activeGame, tile.getPosition());
        }
        int context = 0;
        if (playersWithPds2.size() > 0) {
            context = 1;
        }
        FileUpload systemWithContext;
        if (file == null) {
            systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event, p1);
        } else {
            systemWithContext = file;
        }
        MessageHelper.sendMessageWithFile(tc, systemWithContext, "Picture of system", false);
        List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeGame, tile.getPosition(), p1, p2, spaceOrGround);
        Button graviton = null;
        MessageHelper.sendMessageToChannelWithButtons(tc, "Combat", buttons);
        if (playersWithPds2.size() > 0 && !activeGame.isFoWMode() && "space".equalsIgnoreCase(spaceOrGround)) {
            StringBuilder pdsMessage = new StringBuilder("The following players have space cannon offense cover in the region, and can use the button to fire it:");
            for (Player playerWithPds : playersWithPds2) {
                pdsMessage.append(" ").append(playerWithPds.getRepresentation());
                if (playerWithPds.hasTechReady("gls") && graviton == null) {
                    graviton = Button.secondary("exhaustTech_gls", "Exhaust Graviton Laser Systems");
                }
            }
            MessageHelper.sendMessageToChannel(tc, pdsMessage.toString());
        } else {
            if (activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(tc, "In fog, it is the players responsibility to check for pds2");
            }
        }
        if ("space".equalsIgnoreCase(spaceOrGround)) {
            List<Button> buttons2 = new ArrayList<>();
            buttons2.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_afb", "Roll " + CombatRollType.AFB.getValue()));
            buttons2.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_spacecannonoffence", "Roll Space Cannon Offence"));
            if (!activeGame.isFoWMode()) {
                buttons2.add(Button.danger("declinePDS", "Decline PDS"));
                if ((p1.hasTech("asc") && ButtonHelper.checkNumberNonFighterShips(p1, activeGame, tile) > 2) || (p2.hasTech("asc") && ButtonHelper.checkNumberNonFighterShips(p2, activeGame, tile) > 2)) {
                    buttons2.add(Button.primary("assCannonNDihmohn_asc_" + tile.getPosition(), "Use Assault Cannon").withEmoji(Emoji.fromFormatted(Emojis.WarfareTech)));
                }
                if (FoWHelper.doesTileHaveWHs(activeGame, tile.getPosition()) && (p1.hasTech("ds") || p2.hasTech("ds"))) {
                    buttons2.add(Button.primary("assCannonNDihmohn_ds_" + tile.getPosition(), "Use Dimensional Splicer").withEmoji(Emoji.fromFormatted(Emojis.Ghost)));
                }
                if ((activeGame.playerHasLeaderUnlockedOrAlliance(p1, "dihmohncommander") && ButtonHelper.checkNumberNonFighterShips(p1, activeGame, tile) > 2)
                    || (activeGame.playerHasLeaderUnlockedOrAlliance(p2, "dihmohncommander") && ButtonHelper.checkNumberNonFighterShips(p2, activeGame, tile) > 2)) {
                    buttons2.add(Button.primary("assCannonNDihmohn_dihmohn_" + tile.getPosition(), "Use Dihmohn Commander").withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
                }
                if ((p1.hasAbility("ambush")) || (!activeGame.isFoWMode() && p2.hasAbility("ambush"))) {

                    buttons2.add(Button.secondary("rollForAmbush_" + tile.getPosition(), "Ambush").withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
                }

                buttons2.add(Button.secondary("announceARetreat", "Announce A Retreat"));

            }
            if (graviton != null) {
                buttons2.add(graviton);
            }
            MessageHelper.sendMessageToChannelWithButtons(tc, "You can use these buttons to roll AFB or Space Cannon Offence", buttons2);
        }

        if ((p1.hasTech("dslaner") && p1.getAtsCount() > 0) || (p2.hasTech("dslaner") && p2.getAtsCount() > 0)) {
            List<Button> buttons3 = new ArrayList<>(ButtonHelperFactionSpecific.getLanefirATSButtons(p1, p2));
            MessageHelper.sendMessageToChannelWithButtons(tc, "You can use these buttons to remove commodities from ATS Armaments", buttons3);
        }
    }
}
