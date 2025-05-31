package ti4.commands.ds;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.DiscordantStarsHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.info.AbilityInfoService;

class SetPolicy extends GameStateSubcommand {

    public SetPolicy() {
        super(Constants.SET_POLICY, "Set Policies for Olradin Faction Abilities to their + or - side", true, true);
        List<Choice> people = CommandHelper.toChoices("Connect", "Control", "+", "-");
        List<Choice> economy = CommandHelper.toChoices("Empower", "Exploit", "+", "-");
        List<Choice> environment = CommandHelper.toChoices("Preserve", "Plunder", "+", "-");

        addOptions(new OptionData(OptionType.STRING, Constants.SET_PEOPLE, "Policy - The People: \"Connect ➕\" or \"Control ➖\"").addChoices(people));
        addOptions(new OptionData(OptionType.STRING, Constants.SET_ENVIRONMENT, "Policy - The Environment: \"Preserve ➕\" or \"Plunder ➖\"").addChoices(environment));
        addOptions(new OptionData(OptionType.STRING, Constants.SET_ECONOMY, "Policy - The Economy: \"Empower ➕\" or \"Exploit ➖\"").addChoices(economy));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to set Olradin Policies").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // List<String> playerAbilities = player.getAbilities().stream().sorted().toList();
        OptionMapping policy1 = event.getOption(Constants.SET_PEOPLE);
        OptionMapping policy2 = event.getOption(Constants.SET_ENVIRONMENT);
        OptionMapping policy3 = event.getOption(Constants.SET_ECONOMY);
        String pol1 = null;
        String pol2 = null;
        String pol3 = null;

        if ((policy1 == null) && (policy2 == null) && (policy3 == null)) {
            MessageHelper.sendMessageToEventChannel(event, "Must set at least one Policy.");
            return;
        }

        // break down choices into + or - from their original inputs
        if (policy1 != null) {
            pol1 = policy1.getAsString().toLowerCase();
            pol1 = convertChoice(pol1);
            if (pol1 == null) {
                MessageHelper.sendMessageToEventChannel(event,
                    "received an incorrect input for _Policy - The People_, will either ignore, or default to _Policy - The People: Connect ➕_ if this is your first time setting Policies.");
            }
        }
        if (policy2 != null) {
            pol2 = policy2.getAsString().toLowerCase();
            pol2 = convertChoice(pol2);
            if (pol2 == null) {
                MessageHelper.sendMessageToEventChannel(event,
                    "received an incorrect input for _Policy - The Environment_, will either ignore, or default to _Policy - The Environment: Preserve ➕_ if this is your first time setting Policies.");
            }
        }
        if (policy3 != null) {
            pol3 = policy3.getAsString().toLowerCase();
            pol3 = convertChoice(pol3);
            if (pol3 == null) {
                MessageHelper.sendMessageToEventChannel(event,
                    "received an incorrect input for _Policy - The Economy_, will either ignore, or default to _Policy - The Economy: Empower ➕_ if this is your first time setting Policies.");
            }
        }

        Player player = getPlayer();
        if (!player.hasOlradinPolicies()) {
            MessageHelper.sendMessageToEventChannel(event, "Player does not have **Policy** (Olradin faction ability).");
            return;
        }

        // extra returns for the first time policies are set
        if (player.hasAbility("policies")) {
            player.removeAbility("policies");
            MessageHelper.sendMessageToEventChannel(event, "Initiating Policies for Olradin.");
            if (pol1 == null) {
                pol1 = "+";
                MessageHelper.sendMessageToEventChannel(event,
                    "Need to initially set _Policy - The People. Defaulting to _Policy - The People: Connect ➕_.");
            }
            if (pol2 == null) {
                pol2 = "+";
                MessageHelper.sendMessageToEventChannel(event,
                    "Need to initially set _Policy - The Environment_ policy. Defaulting to _Policy - The Environment: Preserve ➕_.");
            }
            if (pol3 == null) {
                pol3 = "+";
                MessageHelper.sendMessageToEventChannel(event, "Need to initially set _Policy - The Economy_. Defaulting to _Policy - The Economy: Empower ➕_.");
            }
        }
        // MessageHelper.sendMessageToEventChannel(event, "debug finalset - pol1" + pol1 + " pol2 " + pol2 + " pol3 " +
        // pol3); (debug messagesender if more work is needed)

        int negativePolicies = 0;
        int positivePolicies = 0;

        // go through each option and set the policy accordingly
        if (pol1 != null) {
            if ("-".equals(pol1)) {
                if (player.hasAbility("policy_the_people_connect")) {
                    player.removeAbility("policy_the_people_connect");
                    MessageHelper.sendMessageToEventChannel(event, "removed _Policy - The People: Connect ➕_.");
                }
                player.addAbility("policy_the_people_control");
                negativePolicies++;
                MessageHelper.sendMessageToEventChannel(event, "added _Policy - The People: Control ➖_.");
            } else if ("+".equals(pol1)) {
                if (player.hasAbility("policy_the_people_control")) {
                    player.removeAbility("policy_the_people_control");
                    MessageHelper.sendMessageToEventChannel(event, "removed _Policy - The People: Control ➖_.");
                }
                player.addAbility("policy_the_people_connect");
                positivePolicies++;
                MessageHelper.sendMessageToEventChannel(event, "added _Policy - The People: Connect ➕_.");
            }
        }
        if (pol2 != null) {
            if ("-".equals(pol2)) {
                if (player.hasAbility("policy_the_environment_preserve")) {
                    player.removeAbility("policy_the_environment_preserve");
                    MessageHelper.sendMessageToEventChannel(event, "removed _Policy - The Environment: Preserve ➕_.");
                }
                player.addAbility("policy_the_environment_plunder");
                negativePolicies++;
                MessageHelper.sendMessageToEventChannel(event, "added _Policy - The Environment: Plunder ➖_.");
            } else if ("+".equals(pol2)) {
                if (player.hasAbility("policy_the_environment_plunder")) {
                    player.removeAbility("policy_the_environment_plunder");
                    MessageHelper.sendMessageToEventChannel(event, "removed _Policy - The Environment: Plunder ➖_.");
                }
                player.addAbility("policy_the_environment_preserve");
                positivePolicies++;
                MessageHelper.sendMessageToEventChannel(event, "added _Policy - The Environment: Preserve ➕_.");
            }
        }
        if (pol3 != null) {
            if ("-".equals(pol3)) {
                if (player.hasAbility("policy_the_economy_empower")) {
                    player.removeAbility("policy_the_economy_empower");
                    MessageHelper.sendMessageToEventChannel(event, "removed _Policy - The Economy: Empower ➕_.");
                }
                if (!player.hasAbility("policy_the_economy_exploit")) {
                    player.addAbility("policy_the_economy_exploit");
                    player.setCommoditiesTotal(player.getCommoditiesTotal() - 1);
                    MessageHelper.sendMessageToEventChannel(event, "added _Policy - The Economy: Exploit ➖_. Decreased commodities total by 1 (double check the value is correct).");
                } else if (player.hasAbility("policy_the_economy_exploit")) {
                    player.addAbility("policy_the_economy_exploit");
                    MessageHelper.sendMessageToEventChannel(event, "added _Policy - The Economy: Exploit ➖_. You already had this policy, so your commodities total is unchanged.");
                }
                negativePolicies++;
            } else if ("+".equals(pol3)) {
                if (player.hasAbility("policy_the_economy_exploit")) {
                    player.removeAbility("policy_the_economy_exploit");
                    player.setCommoditiesTotal(player.getCommoditiesTotal() + 1);
                    MessageHelper.sendMessageToEventChannel(event, "removed _Policy - The Economy: Exploit ➖_. Increased commodities total by 1 (double check the value is correct).");
                }
                player.addAbility("policy_the_economy_empower");
                positivePolicies++;
                MessageHelper.sendMessageToEventChannel(event, "added _Policy - The Economy: Empower ➕_.");
            }
        }

        player.removeOwnedUnitByID("olradin_mech");
        player.removeOwnedUnitByID("olradin_mech_positive");
        player.removeOwnedUnitByID("olradin_mech_negative");
        String unitModelID;
        if (positivePolicies >= 2) {
            unitModelID = "olradin_mech_positive";
        } else if (negativePolicies >= 2) {
            unitModelID = "olradin_mech_negative";
        } else {
            unitModelID = "olradin_mech";
        }
        player.addOwnedUnitByID(unitModelID);
        UnitModel unitModel = Mapper.getUnit(unitModelID);

        Game game = getGame();
        DiscordantStarsHelper.checkOlradinMech(game);

        AbilityInfoService.sendAbilityInfo(game, player, event);
        MessageHelper.sendMessageEmbedsToCardsInfoThread(player, "", List.of(unitModel.getRepresentationEmbed(false)));
    }

    public static String convertChoice(String inputChoice) {
        if (inputChoice == null) {
            return null;
        }
        if (("exploit".equals(inputChoice)) || ("plunder".equals(inputChoice)) || ("control".equals(inputChoice))
            || ("-".equals(inputChoice))) {
            return "-";
        }
        if (("empower".equals(inputChoice)) || ("preserve".equals(inputChoice)) || ("connect".equals(inputChoice))
            || ("+".equals(inputChoice))) {
            return "+";
        }
        return null;

    }
}
