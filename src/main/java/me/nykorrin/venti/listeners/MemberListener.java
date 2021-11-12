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

        InteractionHook hook = event.getHook();
        GuildMusicManager musicManager = getGuildAudioPlayer(event.getGuild());

        event.deferReply(true).queue();

        switch (event.getName().toLowerCase()) {
            case "play" -> {
                String trackUrl = event.getOption("song").getAsString();
                AudioManager audioManager = event.getGuild().getAudioManager();

                if (!event.getMember().getVoiceState().inVoiceChannel()) {
                    hook.sendMessage(":no_entry: You need to be in a voice channel!").setEphemeral(true).queue();
                    return;
                }

                if (!audioManager.isConnected() && !audioManager.isAttemptingToConnect()) {
                    loadAndPlay(event.getTextChannel(), event.getMember().getVoiceState().getChannel(), trackUrl);
                    hook.sendMessage(":play_pause: Playing `" + trackUrl + "`.").setEphemeral(true).queue();
                } else {
                    hook.sendMessage(":no_entry: Music bot is already in a channel.").setEphemeral(true).queue();
                }
            }
            case "stop" -> {
                if (event.getGuild().getAudioManager().isConnected()) {
                    musicManager.player.stopTrack();
                    event.getGuild().getAudioManager().closeAudioConnection();
                    hook.sendMessage(" :wave: See ya next time!").setEphemeral(true).queue();
                } else {
                    hook.sendMessage(":no_entry: Music bot is not connected.").setEphemeral(true).queue();
                }
            }
            case "skip" -> {
                if (musicManager.scheduler.getQueue().isEmpty()) {
                    hook.sendMessage(":no_entry: Track queue is empty.").setEphemeral(true).queue();
                } else {
                    skipTrack(event.getTextChannel());
                    hook.sendMessage(":track_next: Playing the next track!").setEphemeral(true).queue();
                }
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

    private void loadAndPlay(final TextChannel channel, VoiceChannel voice, String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());

        if (!isUrl(trackUrl)) {
            trackUrl = "ytsearch:" + trackUrl;
        }

        String finalTrackUrl = trackUrl; // WAY TOO LAZY TO FIX THIS TOO

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                channel.sendMessage(":musical_note: Adding to queue `" + track.getInfo().title + "`").queue();

                play(channel.getGuild(), musicManager, voice, track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                channel.sendMessage(":musical_note: Adding to queue `" + firstTrack.getInfo().title + "` (first track of playlist `" + playlist.getName() + "`)").queue();

                play(channel.getGuild(), musicManager, voice, firstTrack);
            }

            @Override
            public void noMatches() {
                channel.sendMessage(":no_entry: Nothing found by " + finalTrackUrl).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                channel.sendMessage(":no_entry: Could not play: `" + exception.getMessage() + "`").queue();
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, VoiceChannel channel,  AudioTrack track) {
        guild.getAudioManager().openAudioConnection(channel);

        musicManager.scheduler.queue(track);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild());
        musicManager.scheduler.nextTrack();

        channel.sendMessage(":track_next: Skipping... Now playing `" + musicManager.player.getPlayingTrack().getInfo().title + "`").queue();
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
