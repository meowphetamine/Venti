package me.nykorrin.venti;

import me.nykorrin.venti.listeners.MemberListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.util.Collections;

public class Venti {

    public static void main(String[] args) {
        try {
            JDA jda = JDABuilder.createDefault("You thought I'd leave this here?", Collections.emptyList())
                    .addEventListeners(new MemberListener())
                    .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
                    .enableCache(CacheFlag.VOICE_STATE)
                    .setActivity(Activity.playing("some music..."))
                    .build();

            CommandListUpdateAction commands = jda.updateCommands();

            commands.addCommands(
                    new CommandData("play", "Play some tunes!")
                            .addOptions(new OptionData(OptionType.STRING, "song", "Song URL/Name")
                                    .setRequired(true))
            );
            commands.addCommands(
                    new CommandData("stop", "Stop the tunes.")
            );
            commands.addCommands(
                    new CommandData("skip", "Skip a song.")
            );
            commands.queue();
            jda.awaitReady();
        } catch (LoginException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
