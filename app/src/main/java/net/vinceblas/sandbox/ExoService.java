package net.vinceblas.sandbox;

import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioTrack;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.util.Util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//@SuppressWarnings("unused")
public class ExoService extends Service {

    public static final String URL_HYRULE_STREAM = "http://listen.radiohyrule.com/listen.aac";

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;
    private static final String LOG_TAG = ExoService.class.getSimpleName();
    public static final String USER_AGENT_PREFIX = "VB_Sandbox"; //todo change this?
    private MediaCodecAudioTrackRenderer audioRenderer;

    public interface PlaybackStatusListener {

        int STATUS_PLAYING = 1;
        int STATUS_PAUSED = 2;
        int STATUS_BUFFERING = 3;

        void onPlaybackStatusChanged(int status);
        void onError(Throwable throwable);
        void onNewMetadataAvailable(Map<String, String> metadata);
    }

    private Handler eventHandler;
    private ExoPlayer exoPlayer;

    private Set<PlaybackStatusListener> listeners;

    @Override
    public IBinder onBind(Intent intent) {
        return new PlaybackBinder(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        listeners = new HashSet<>(1);
        eventHandler = new Handler();
        exoPlayer = ExoPlayer.Factory.newInstance(1, 500, 500);

        final Uri uri = Uri.parse(URL_HYRULE_STREAM);
        final String userAgent = Util.getUserAgent(this, USER_AGENT_PREFIX);

        final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(eventHandler, null);
        final Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);

        final IcyHttpDataSource dataSource = new IcyHttpDataSource(userAgent, null, bandwidthMeter);
        dataSource.setRequestProperty("Icy-MetaData", "1");
        dataSource.setIcyMetaDataCallback(new IcyInputStream.IcyMetadataCallback() {
            @Override
            public void playerMetadata(String key, String value) {
                //todo update notification
                Log.d(LOG_TAG, "ExoPlayer Metadata: { " + key + " : \"" + value + "\" }");
                notifyMetadata(Collections.singletonMap(key, value));
            }
        });

        final ExtractorSampleSource sampleSource = new ExtractorSampleSource(uri, dataSource, allocator,
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE, new AdtsExtractor());

        audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
                MediaCodecSelector.DEFAULT, null, true, eventHandler, audioTrackRendererEventListener,
                AudioCapabilities.getCapabilities(this), AudioManager.STREAM_MUSIC);

        exoPlayer.addListener(exoPlayerListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        eventHandler.removeCallbacksAndMessages(null);
        exoPlayer.release();
    }

    //todo make sticky

    public void startPlayback(){
        //todo foreground notification
        startSelf();
        if(exoPlayer.getPlaybackState() == ExoPlayer.STATE_IDLE){
            exoPlayer.prepare(audioRenderer);
            Log.v(LOG_TAG, "exoPlayer preparing");
        } else if (!exoPlayer.getPlayWhenReady()) {
            exoPlayer.seekTo(0); //move to "live edge" of stream
        }
        exoPlayer.setPlayWhenReady(true);
    }

    public void stopPlayback(){
        //todo un-foreground notification
        stopSelf(); //we'll actually keep running if something is bound to us
        exoPlayer.setPlayWhenReady(false);
    }

    /**
     * Registers a new listener to receive event callbacks from the service.<br />
     * Executes an immediate callback with the current playback status as soon as it is added
     */
    public void registerNewPlaybackStatusListener(PlaybackStatusListener listener){
        listeners.add(listener);
        listener.onPlaybackStatusChanged(getCurrentPlaybackStatus());
    }

    public void unregisterListener(PlaybackStatusListener listener){
        listeners.remove(listener);
    }

    public int getCurrentPlaybackStatus() {
        return mapPlaybackStatus(exoPlayer.getPlayWhenReady(), exoPlayer.getPlaybackState());
    }

    private void notifyStatusChanged(int status){ //todo inline?
        for(PlaybackStatusListener listener : listeners){
            listener.onPlaybackStatusChanged(status);
        }
    }

    private void notifyError(Throwable throwable){
        for(PlaybackStatusListener listener : listeners){
            listener.onError(throwable);
        }
    }

    private void notifyMetadata(Map<String, String> data){
        for(PlaybackStatusListener listener : listeners){
            listener.onNewMetadataAvailable(data);
        }
    }

    private void startSelf(){
        Intent intent = new Intent(this, ExoService.class);
        startService(intent);
    }

    ExoPlayer.Listener exoPlayerListener = new ExoPlayer.Listener() {
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            int status;

            if(playbackState == ExoPlayer.STATE_READY){
                Log.v(LOG_TAG, "exoPlayer Ready");
            } else if (playbackState == ExoPlayer.STATE_ENDED){
                //this is bad, and unrecoverable for now
                notifyError(new IllegalStateException("ExoPlayer ended playback unexpectedly"));
            }
            else {
                Log.v(LOG_TAG, "exoPlayer waiting: " + playbackState);
            }

            status = mapPlaybackStatus(playWhenReady, playbackState);
            notifyStatusChanged(status);
        }

        @Override
        public void onPlayWhenReadyCommitted() { }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            Log.w(LOG_TAG, error);
            notifyError(error);
        }
    };

    //Convert 2D ExoPlayer status into 1D status for clients
    private static int mapPlaybackStatus(boolean playWhenReady, int playbackState) {
        int status;
        switch (playbackState) {
            case ExoPlayer.STATE_READY:
                if (playWhenReady) {
                    //playing
                    status = PlaybackStatusListener.STATUS_PLAYING;
                } else {
                    //paused
                    status = PlaybackStatusListener.STATUS_PAUSED;
                }
                break;
            case ExoPlayer.STATE_IDLE:
                status = PlaybackStatusListener.STATUS_PAUSED;
                break;
            default:
                status = PlaybackStatusListener.STATUS_BUFFERING;

        }
        return status;
    }

    private MediaCodecAudioTrackRenderer.EventListener audioTrackRendererEventListener = new MediaCodecAudioTrackRenderer.EventListener() {

        @Override
        public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {}

        @Override
        public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {}

        @Override
        public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
            notifyError(e);
            Log.d(LOG_TAG, "MediaCodecAudioTrackRenderer onAudioTrackInitializationError Error: ", e);
        }

        @Override
        public void onAudioTrackWriteError(AudioTrack.WriteException e) {
            notifyError(e);
            Log.d(LOG_TAG, "MediaCodecAudioTrackRenderer onAudioTrackWriteError Error: ", e);
        }

        @Override
        public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
            notifyError(e);
            Log.d(LOG_TAG, "MediaCodecAudioTrackRenderer onDecoderInitializationError Error: ", e);
        }

        @Override
        public void onCryptoError(MediaCodec.CryptoException e) {
            notifyError(e);
            Log.d(LOG_TAG, "MediaCodecAudioTrackRenderer onCryptoError Error: ", e);
        }
    };

    public class PlaybackBinder extends Binder{

        private final ExoService exoService;

        public PlaybackBinder(ExoService service){
            exoService = service;
        }

        public ExoService getExoService(){
            return exoService;
        }
    }
}
