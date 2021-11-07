package me.nykorrin.venti.listeners;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import me.nykorrin.venti.music.GuildMusicManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.managers.AudioManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class MemberListener extends ListenerAdapter {

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    public MemberListener() {
        this.musicManagers = new HashMap<>();

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        if (event.getUser().isBot()) return;

        User user = event.getUser();
        InteractionHook hook = event.getHook();
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());

        event.deferReply(true).queue();

        switch (event.getName().toLowerCase()) {
            case "play": {
                String trackUrl = event.getOption("song").getAsString();
                AudioManager audioManager = event.getGuild().getAudioManager();

                loadAndPlay(event.getTextChannel(), trackUrl);

                if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
                    audioManager.openAudioConnection(event.getMember().getVoiceState().getChannel()); // Easy bypass to the bot not connecting on start but I'm way too lazy to actually fix it
                    hook.sendMessage("Playing " + trackUrl + ".").setEphemeral(true).queue();
                } else {
                    hook.sendMessage("Music bot is already in a channel.").setEphemeral(true).queue();
                }
                break;
            }
            case "stop": {
                if (event.getGuild().getAudioManager().isConnected()) {
                    musicManager.player.stopTrack();
                    event.getGuild().getAudioManager().closeAudioConnection();
                    hook.sendMessage("See ya next time!").setEphemeral(true).queue();
                } else {
                    hook.sendMessage("Music bot is not connected.").setEphemeral(true).queue();
                }
                break;
            }
            case "skip": {
                if (musicManager.scheduler.getQueue().isEmpty()) {
                    hook.sendMessage("Track queue is empty.").setEphemeral(true).queue();
                } else {
                    skipTrack(event.getTextChannel());
                    hook.sendMessage("Playing the next track!").setEphemeral(true).queue();
                }
                break;
            }
        }
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (isAlone(event.getGuild())) {
            event.getGuild().getAudioManager().closeAudioConnection();
        }
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = Long.parseLong(guild.getId());
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());

        return musicManager;
    }

    private void loadAndPlay(final TextChannel channel, String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        if (!isUrl(trackUrl)) {
            trackUrl = "ytsearch:" + trackUrl;
        }

        String finalTrackUrl = trackUrl; // WAY TOO LAZY TO FIX THIS TOO

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage("Adding to queue " + track.getInfo().title).queue();

                play(channel.getGuild(), musicManager, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage("Adding to queue " + firstTrack.getInfo().title + " (first track of playlist " + playlist.getName() + ")").queue();

                play(channel.getGuild(), musicManager, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage("Nothing found by " + finalTrackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage("Could not play: " + exception.getMessage()).queue();
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track) {
        connectToFirstVoiceChannel(guild.getAudioManager());

        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage("Skipped to next track.").queue();
    }

    private static void connectToFirstVoiceChannel(AudioManager audioManager) {
        if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
            for (VoiceChannel voiceChannel : audioManager.getGuild().getVoiceChannels()) {
                audioManager.openAudioConnection(voiceChannel);
                break;
            }
        }
    }

    private boolean isUrl(String url) {
        try {
            new URI(url);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isAlone(Guild guild) {
        if (guild.getAudioManager().getConnectedChannel() == null) return false;

        return guild.getAudioManager().getConnectedChannel().getMembers().stream().noneMatch(members -> !members.getVoiceState().isDeafened() && !members.getUser().isBot());
    }
}
