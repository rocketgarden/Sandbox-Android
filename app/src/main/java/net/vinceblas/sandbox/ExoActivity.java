package net.vinceblas.sandbox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

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
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.Util;

import butterknife.Bind;
import butterknife.ButterKnife;

@SuppressLint("SetTextI18n")
public class ExoActivity extends Activity {

    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENT_COUNT = 256;

    @Bind(R.id.textView) TextView textView;
    @Bind(R.id.button) Button button1;
    @Bind(R.id.button2) Button button2;
    @Bind(R.id.button3) Button button3;
    @Bind(R.id.button4) Button button4;
    public static final String URL_HYRULE_STREAM = "http://listen.radiohyrule.com/listen.aac";
    private Handler eventHandler;
    private ExoPlayer exoPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        button1.setText("Play");
        button2.setText("Pause");

        button3.setText("x");
        button4.setText("x");
        button3.setEnabled(false);
        button4.setEnabled(false);

        textView.setText("Idle");

        final Uri uri = Uri.parse(URL_HYRULE_STREAM);

        exoPlayer = ExoPlayer.Factory.newInstance(1, 500, 500);

        exoPlayer.addListener(new ExoPlayer.Listener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                switch(playbackState){
                    case ExoPlayer.STATE_READY:
                        Log.v("EXO_VB", "exoPlayer Ready");
                        button1.setEnabled(true);
                        textView.setText(playWhenReady ? "Playing" : "Paused");
                        break;
                    default:
                        Log.v("EXO_VB", "exoPlayer waiting: " + playbackState);
//                        button1.setEnabled(false);
                        textView.setText("Preparing");
                }

                textView.setTextColor(playWhenReady ? Color.GREEN : Color.RED);
            }

            @Override
            public void onPlayWhenReadyCommitted() {

            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.w("EXO_VB_ERR", error);
            }
        });

        eventHandler = new Handler();

        String userAgent = Util.getUserAgent(this, "VinceBlas");

        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(eventHandler, null);
        Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);

        DataSource dataSource = new DefaultUriDataSource(this, bandwidthMeter, userAgent);

        ExtractorSampleSource sampleSource = new ExtractorSampleSource(uri, dataSource, allocator,
                BUFFER_SEGMENT_COUNT * BUFFER_SEGMENT_SIZE, new AdtsExtractor());


        final MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource,
                MediaCodecSelector.DEFAULT, null, true, eventHandler, createEventListener(),
                AudioCapabilities.getCapabilities(this), AudioManager.STREAM_MUSIC);

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.v("EXO_VB", "Play");
                if(exoPlayer.getPlaybackState() == ExoPlayer.STATE_IDLE){
                    exoPlayer.prepare(audioRenderer);
                    Log.v("EXO_VB", "exoPlayer preparing");
                }
                exoPlayer.seekTo(0);
                exoPlayer.setPlayWhenReady(true);

            }
        });

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exoPlayer.setPlayWhenReady(false);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exoPlayer.release();
        eventHandler.removeCallbacksAndMessages(null);
    }

    private MediaCodecAudioTrackRenderer.EventListener createEventListener(){
        return new MediaCodecAudioTrackRenderer.EventListener() {

            @Override
            public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
                Log.i("EXO_VB_NFO", "onAudioTrackUnderrun, elapsedSinceLastFeedMs: " + elapsedSinceLastFeedMs);
            }

            @Override
            public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
                Log.i("EXO_VB_NFO", "onDecoderInitialized");

            }

            @Override
            public void onAudioTrackInitializationError(AudioTrack.InitializationException e) {
                Log.w("EXO_VB_WRN", "onAudioTrackInitializationError Error: ", e);
            }

            @Override
            public void onAudioTrackWriteError(AudioTrack.WriteException e) {
                Log.w("EXO_VB_WRN", "onAudioTrackWriteError Error: ", e);
            }

            @Override
            public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {
                Log.w("EXO_VB_WRN", "onDecoderInitializationError Error: ", e);
            }

            @Override
            public void onCryptoError(MediaCodec.CryptoException e) {
                Log.w("EXO_VB_WRN", "onCryptoError Error: ", e);
            }
        };
    }
}
