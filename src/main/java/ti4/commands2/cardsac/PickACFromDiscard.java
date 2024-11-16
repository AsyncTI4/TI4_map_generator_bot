package ti4.commands2.cardsac;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;

class PickACFromDiscard extends GameStateSubcommand {

    public PickACFromDiscard() {
        super(Constants.PICK_AC_FROM_DISCARD, "Pick an Action Card from discard pile into your hand", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();
        ActionCardHelper.getActionCardFromDiscard(event, getGame(), getPlayer(), acIndex);
    }

    @ButtonHandler("codexCardPick_")
    public static void pickACardFromDiscardStep1(GenericInteractionCreateEvent event, Game game, Player player) {
        ActionCardHelper.pickACardFromDiscardStep1(event, game, player);
    }

    @ButtonHandler("pickFromDiscard_")
    public static void pickACardFromDiscardStep2(Game game, Player player, ButtonInteractionEvent event,        String buttonID) {
        ActionCardHelper.pickACardFromDiscardStep2(game, player, event, buttonID);
    }
}
