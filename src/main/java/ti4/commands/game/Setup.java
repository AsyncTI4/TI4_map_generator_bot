package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class Setup extends GameStateSubcommand {
    public Setup() {
        super(Constants.SETUP, "Game Setup");
        addOptions(new OptionData(OptionType.INTEGER, Constants.PLAYER_COUNT_FOR_MAP, "Number of players between 1 or 30. Default 6"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.VP_COUNT, "Game VP count. Default is 10"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.SC_COUNT_FOR_MAP, "Number of strategy cards each player gets. Default 1"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MAX_SO_COUNT, "Max Number of SO's per player. Default 3"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_CUSTOM_NAME, "Custom description"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.TIGL_GAME, "True to mark the game as TIGL"));
        addOptions(new OptionData(OptionType.INTEGER, Constants.AUTO_PING, "Hours between auto pings. Min 1. Enter 0 to turn off."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping playerCount = event.getOption(Constants.PLAYER_COUNT_FOR_MAP);
        if (playerCount != null) {
            int count = playerCount.getAsInt();
            if (count < 1 || count > 30) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must specify between 1 or 30 players.");
            } else {
                game.setPlayerCountForMap(count);
            }

        }

        OptionMapping vpOption = event.getOption(Constants.VP_COUNT);
        if (vpOption != null) {
            int count = vpOption.getAsInt();
            if (count < 1) {
                count = 1;
            } else if (count > 20) {
                count = 20;
            }
            game.setVp(count);
        }

        Integer maxSOCount = event.getOption(Constants.MAX_SO_COUNT, null, OptionMapping::getAsInt);
        if (maxSOCount != null && maxSOCount >= 0) {
            game.setMaxSOCountPerPlayer(maxSOCount);

            String key = "factionsThatAreNotDiscardingSOs";
            String key2 = "queueToDrawSOs";
            String key3 = "potentialBlockers";
            game.setStoredValue(key, "");
            game.setStoredValue(key2, "");
            game.setStoredValue(key3, "");
            if (game.getRound() > 1) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Any SO queue has been erased due to the change in SO count. You can resolve the previously queued draws by just pressing draw again.");
            }

        }

        Integer scCountPerPlayer = event.getOption(Constants.SC_COUNT_FOR_MAP, null, OptionMapping::getAsInt);
        if (scCountPerPlayer != null) {
            int maxSCsPerPlayer;
            if (game.getRealPlayers().isEmpty()) {
                maxSCsPerPlayer = game.getSCList().size() / Math.max(1, game.getPlayers().size());
            } else {
                maxSCsPerPlayer = game.getSCList().size() / Math.max(1, game.getRealPlayers().size());
            }

            if (maxSCsPerPlayer == 0) maxSCsPerPlayer = 1;

            if (scCountPerPlayer < 1) {
                scCountPerPlayer = 1;
            } else if (scCountPerPlayer > maxSCsPerPlayer) {
                scCountPerPlayer = maxSCsPerPlayer;
            }
            game.setStrategyCardsPerPlayer(scCountPerPlayer);
        }

        Integer pingHours = event.getOption(Constants.AUTO_PING, null, OptionMapping::getAsInt);
        if (pingHours != null) {
            if (pingHours == 0) {
                game.setAutoPing(false);
                game.setAutoPingSpacer(pingHours);
            } else {
                game.setAutoPing(true);
                if (pingHours < 1) {
                    pingHours = 1;
                }
                game.setAutoPingSpacer(pingHours);
            }
        }

        String customGameName = event.getOption(Constants.GAME_CUSTOM_NAME, null, OptionMapping::getAsString);
        if (customGameName != null) {
            game.setCustomName(customGameName);
        }

        if (!setGameMode(event, game)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Something went wrong and the game modes could not be set, please see error above.");
        }
    }

    public static boolean setGameMode(SlashCommandInteractionEvent event, Game game) {
        if (event.getOption(Constants.TIGL_GAME) == null && event.getOption(Constants.ABSOL_MODE) == null && event.getOption(Constants.DISCORDANT_STARS_MODE) == null
            && event.getOption(Constants.BASE_GAME_MODE) == null && event.getOption(Constants.MILTYMOD_MODE) == null) {
            return true; //no changes were made
        }
        boolean isTIGLGame = event.getOption(Constants.TIGL_GAME, game.isCompetitiveTIGLGame(), OptionMapping::getAsBoolean);
        boolean absolMode = event.getOption(Constants.ABSOL_MODE, game.isAbsolMode(), OptionMapping::getAsBoolean);
        boolean miltyModMode = event.getOption(Constants.MILTYMOD_MODE, game.isMiltyModMode(), OptionMapping::getAsBoolean);
        boolean discordantStarsMode = event.getOption(Constants.DISCORDANT_STARS_MODE, game.isDiscordantStarsMode(), OptionMapping::getAsBoolean);
        boolean baseGameMode = event.getOption(Constants.BASE_GAME_MODE, game.isBaseGameMode(), OptionMapping::getAsBoolean);
        boolean votcMode = event.getOption(Constants.VOTC_MODE, game.isVotcMode(), OptionMapping::getAsBoolean);
        return WeirdGameSetup.setGameMode(event, game, baseGameMode, absolMode, miltyModMode, discordantStarsMode, isTIGLGame, votcMode);
    }
}
