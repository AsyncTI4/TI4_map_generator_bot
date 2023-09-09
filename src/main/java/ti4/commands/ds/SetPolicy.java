package ti4.commands.ds;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

public class SetPolicy extends DiscordantStarsSubcommandData {

    public SetPolicy() {
        super(Constants.SET_POLICY, "Set Policies for Olradin Faction Abilities to their + or - side");
        addOptions(new OptionData(OptionType.STRING, Constants.SET_PEOPLE, "Policy: The People Choice - 'Connect (+)' or 'Control (-)'").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SET_ENVIRONMENT,"Policy: The Environment Choice - 'Preserve (+)' or 'Plunder (-)'").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SET_ECONOMY, "Policy: The Economy Choice - 'Empower (+)' or 'Exploit (-)'").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to set Olradin Policies").setAutoComplete(true));
        }
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        List<String> playerAbilities = player.getAbilities().stream().sorted().toList();
        OptionMapping policy1 = event.getOption(Constants.SET_PEOPLE);
        OptionMapping policy2 = event.getOption(Constants.SET_ENVIRONMENT);
        OptionMapping policy3 = event.getOption(Constants.SET_ECONOMY);
        String pol1 = null;
        String pol2 = null;
        String pol3 = null;
        
        if (( policy1 == null) && ( policy2 == null) && ( policy3 == null))
        {
            sendMessage("Must set at least one Policy!");
            return;
        }

        // break down choices into + or - from their original inputs
        if (policy1 != null) 
        {
            pol1 = policy1.getAsString().toLowerCase();
            pol1 = convertChoice(pol1);
            if(pol1 == null) {sendMessage("received an incorrect input for Policy: The People, will either ignore or default to + if this is your first time setting policies");}
        }
        if (policy2 != null) 
        {
            pol2 = policy2.getAsString().toLowerCase();
            pol2 = convertChoice(pol2);
            if(pol2 == null) {sendMessage("received an incorrect input for Policy: The Environment, will either ignore or default to + if this is your first time setting policies");}
            }
        if(policy3 != null)
        {
            pol3 = policy3.getAsString().toLowerCase();
            pol3 = convertChoice(pol3);
            if(pol3 == null) {sendMessage("received an incorrect input for Policy: The Economy, will either ignore or default to + if this is your first time setting policies");}
        }
        


        //sendMessage("debug  postset - pol1" + pol1 + " pol2 " + pol2 + " pol3 " + pol3); (debug messagesender if more work is needed)
        
        if ((!player.hasAbility("policies")) && (!player.hasAbility("policy_the_people_connect")) && (!player.hasAbility("policy_the_people_control")) 
                                            && (!player.hasAbility("policy_the_environment_preserve")) && (!player.hasAbility("policy_the_environment_plunder")) 
                                            && (!player.hasAbility("policy_the_economy_empower")) && (!player.hasAbility("policy_the_economy_exploit")) )
        {
                sendMessage("Player does not have Policy (Olradin Faction Ability)");
                return;
        }
        // extra returns for the first time policies are set
        if (player.hasAbility("policies"))
        {
            player.removeAbility("policies");
            sendMessage("Initiating Policies for Olradin.");
            if (pol1 == null) 
            {
                pol1 = "+";
                sendMessage("Need to initially set People policy. Defaulting to Policy - The People: Connect (+).");
            }
            if (pol2 == null) 
            {
                pol2 = "+";
                sendMessage("Need to initially set Environment policy. Defaulting to Policy - The Environment: Preserve (+).");
            }
            if (pol3 == null) 
            {
                pol3 = "+";
                sendMessage("Need to initially set Economy policy. Defaulting to Policy - The Economy: Empower (+).");
            }
        }            
        //sendMessage("debug  finalset - pol1" + pol1 + " pol2 " + pol2 + " pol3 " + pol3); (debug messagesender if more work is needed)
        
        // go through each option and set the policy accordingly
        if (pol1 != null) {
            if ("-".equals(pol1))
            {   
                if(player.hasAbility("policy_the_people_connect")) 
                {
                    player.removeAbility("policy_the_people_connect");
                    sendMessage("removed Policy - The People: Connect (+).");
                }            
                player.addAbility("policy_the_people_control");
                sendMessage("added Policy - The People: Control (-).");
            }       
                else if ("+".equals(pol1))
                {   
                    if(player.hasAbility("policy_the_people_control"))
                    {
                        player.removeAbility("policy_the_people_control"); 
                        sendMessage("removed Policy - The People: Control (-).");
                    }            
                    player.addAbility("policy_the_people_connect");
                    sendMessage("added Policy - The People: Connect (+).");
                }
        }
        if (pol2 != null) {
            if ("-".equals(pol2))
            {   
                if(player.hasAbility("policy_the_environment_preserve")) 
                {
                    player.removeAbility("policy_the_environment_preserve");
                    sendMessage("removed Policy - The Environment: Preserve (+).");
                }            
                player.addAbility("policy_the_environment_plunder");
                sendMessage("added Policy - The Environment: Plunder (-).");
            }
            else if ("+".equals(pol2))
                {
                    if(player.hasAbility("policy_the_environment_plunder"))
                    {
                        player.removeAbility("policy_the_environment_plunder");
                        sendMessage("removed Policy - The Environment: Plunder (-).");
                    }            
                    player.addAbility("policy_the_environment_preserve");
                    sendMessage("added Policy - The Environment: Preserve (+).");
                }
        }
        if (pol3 != null){
            if ("-".equals(pol3))
            {   
                if(player.hasAbility("policy_the_economy_empower"))
                {
                    player.removeAbility("policy_the_economy_empower"); 
                    sendMessage("removed Policy - The Economy: Empower (+).");
                }            
                if (!player.hasAbility("policy_the_economy_exploit")) 
                {
                    player.addAbility("policy_the_economy_exploit"); 
                    player.setCommoditiesTotal(player.getCommoditiesTotal()-1);
                    sendMessage("added Policy - The Economy: Exploit (-). Decreased Commodities total by 1 - double check the value is correct!");
                }
                else if (player.hasAbility("policy_the_economy_exploit")){
                    player.addAbility("policy_the_economy_exploit"); 
                    sendMessage("added Policy - The Economy: Exploit (-). You already had this policy, so your Commodities total is unchanged.");
                }
            }            
            else if ("+".equals(pol3))
                {   
                    if(player.hasAbility("policy_the_economy_exploit"))
                    {
                        player.removeAbility("policy_the_economy_exploit");
                        player.setCommoditiesTotal(player.getCommoditiesTotal()+1);
                        sendMessage("removed Policy - The Economy: Exploit (-). Increased Commodities total by 1 - double check the value is correct!.");
                    }            
                    player.addAbility("policy_the_economy_empower");
                    sendMessage("added Policy - The Economy: Empower (+).");
                }
        }
    }    

        public String convertChoice(String inputChoice) {
            if (inputChoice == null) { return null;}
            if (("exploit".equals(inputChoice)) || ("plunder".equals(inputChoice)) || ("control".equals(inputChoice)) || ("-".equals(inputChoice)))
                {
                  return "-";
                }
            if (("empower".equals(inputChoice)) || ("preserve".equals(inputChoice)) || ("connect".equals(inputChoice)) || ("+".equals(inputChoice)))  {
              return "+";
                }
            return null;
 


        }
}   
        

        
