package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.draft.FrankenDraft;
import ti4.draft.OnePickFrankenDraft;
import ti4.draft.PoweredFrankenDraft;
import ti4.helpers.Constants;
import ti4.helpers.FrankenDraftHelper;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class StartFrankenDraft extends FrankenSubcommandData {
    public StartFrankenDraft() {
        super(Constants.START_FRANKEN_DRAFT, "Start a franken draft");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.FORCE, "'True' to forcefully overwrite existing faction setups (Default: False)"));
        addOptions(new OptionData(OptionType.STRING, Constants.DRAFT_MODE, "Special draft mode").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();

        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!force && game.getPlayers().values().stream().anyMatch(Player::isRealPlayer)) {
            String message = "There are players that are currently set up already. Please rerun the command with the force option set to True to overwrite them.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
            return;
        }

        String draftOption = event.getOption(Constants.DRAFT_MODE, "", OptionMapping::getAsString);
        FrankenDraftMode draftMode = FrankenDraftMode.fromString(draftOption);
        if (!"".equals(draftOption) && draftMode == null) {
          MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid draft mode.");
          return;
        }

        FrankenDraftHelper.setUpFrankenFactions(game, event, force);
        FrankenDraftHelper.clearPlayerHands(game);

        if (draftMode == null) {
            game.setBagDraft(new FrankenDraft(game));
        } else {
            switch (draftMode) {
                case POWERED -> game.setBagDraft(new PoweredFrankenDraft(game));
                case ONEPICK -> game.setBagDraft(new OnePickFrankenDraft(game));
            }
        }

        FrankenDraftHelper.startDraft(game);
        GameSaveLoadManager.saveGame(game, event);
    }

    public enum FrankenDraftMode {
      POWERED("powered", "Adds 1 extra faction tech/ability to pick from."), 
      ONEPICK("onepick", "Draft 1 item a time.");

      private final String name;
      private final String description;

      FrankenDraftMode(String name, String description) {
          this.name = name;
          this.description = description;
      }
    
      @Override
      public String toString() {
          return super.toString().toLowerCase();
      }

      public static FrankenDraftMode fromString(String id) {
          for (FrankenDraftMode mode : values()) {
              if (id.equals(mode.toString())) {
                  return mode;
              }
          }
          return null;
      }
      public String getAutoCompleteName() {
          return name + ": " + description;
      }

      public boolean search(String searchString) {
          return name.toLowerCase().contains(searchString) || description.toLowerCase().contains(searchString) || toString().contains(searchString);
      }
    }

}


