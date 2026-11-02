package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.draft.PlayerSetupService;
import ti4.service.draft.PlayerSetupState;
import ti4.service.testbed.TestBedService;

class SetupBotPlayer extends GameStateSubcommand {

    SetupBotPlayer() {
        super(Constants.SETUP_BOT_PLAYER, "Bot Player initialisation: Faction and Color", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.HS_TILE_POSITION,
                        "Home system tile position (or equivalent e.g. Creuss Gate)")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.BOOLEAN,
                        Constants.NPC,
                        "Is the bot an NPC? (acts like a player, picking SCs and passing)")
                .setRequired(true));
        addOptions(new OptionData(
                        OptionType.BOOLEAN, Constants.DUMMY, "Is the bot a dummy? (no SC pick, like the neutral)")
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setAutoComplete(true));

        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SPEAKER, "True to set player as speaker."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String faction = event.getOption(Constants.FACTION, null, OptionMapping::getAsString);
        if (faction != null) {
            faction = StringUtils.substringBefore(faction.toLowerCase().replace("the ", ""), " ");
        }

        faction = AliasHandler.resolveFaction(faction);
        if (!Mapper.isValidFaction(faction)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Faction `" + faction + "` is not valid. Valid options are: " + Mapper.getFactionIDs());
            return;
        }

        Player player = null;

        String botUserID = TestBedService.VIRTUAL_SEAT_ID_PREFIX;
        for (int x = 1; x < 100; x++) {
            if (!game.getPlayerIDs().contains(botUserID + x)) {
                botUserID += x;
                game.addPlayer(botUserID, "Bot User " + x);
                break;
            }
        }
        player = game.getPlayer(botUserID);
        player.setBotPlayer(true);

        if (event.getOption(Constants.NPC) != null) {
            Boolean npc = event.getOption(Constants.NPC, OptionMapping::getAsBoolean);
            player.setNpc(npc);
            player.setDummy(!npc);
        }

        if (event.getOption(Constants.DUMMY) != null) {
            Boolean dummy = event.getOption(Constants.DUMMY, OptionMapping::getAsBoolean);
            player.setDummy(dummy);
            player.setNpc(!dummy);
        }

        String eventColor = event.getOption(Constants.COLOR, OptionMapping::getAsString);
        String color = eventColor == null ? null : AliasHandler.resolveColor(eventColor.toLowerCase());
        if (color != null && !Mapper.isValidColor(color)) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Color `" + color + "` is not valid. Options are: " + Mapper.getColors());
            return;
        }

        // SPEAKER
        boolean setSpeaker = event.getOption(Constants.SPEAKER, Boolean.FALSE, OptionMapping::getAsBoolean);
        // Substring to grab "305" from "305 Moll Primus (Mentak)" autocomplete
        String positionHS = StringUtils.substringBefore(
                event.getOption(Constants.HS_TILE_POSITION, "", OptionMapping::getAsString), " ");
        PlayerSetupState setupState = new PlayerSetupState(color, faction, positionHS, setSpeaker);
        PlayerSetupService.setupPlayer(setupState, player, game, event);
    }
}
