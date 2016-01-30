package net.vinceblas.sandbox.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import net.vinceblas.sandbox.ExoService;
import net.vinceblas.sandbox.R;

import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;

@SuppressLint("SetTextI18n")
public class ExoServiceActivity extends Activity {

    private static final String LOG_TAG = ExoServiceActivity.class.getSimpleName();


    @Bind(R.id.textView) TextView textView;
    @Bind(R.id.button)  Button button1;
    @Bind(R.id.button2) Button button2;
    @Bind(R.id.button3) Button button3;
    @Bind(R.id.button4) Button button4;

    @Nullable
    private ExoService exoService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        button1.setText("Play");
        button2.setText("Pause");

        button3.setVisibility(View.GONE);
        button4.setVisibility(View.GONE);

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(exoService!= null){
                    exoService.startPlayback();
                }
            }
        });

        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(exoService!= null){
                    exoService.stopPlayback();
                }
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, ExoService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (exoService != null) {
            exoService.unregisterListener(playbackStatusListener);
        }
        unbindService(serviceConnection);
        exoService = null;

    }

    private ExoService.PlaybackStatusListener playbackStatusListener = new ExoService.PlaybackStatusListener() {
        @Override
        public void onPlaybackStatusChanged(int status) {
            switch (status) {
                case STATUS_PLAYING:
                    textView.setText("Playing");
                    break;
                case STATUS_PAUSED:
                    textView.setText("Paused");
                    break;
                case STATUS_BUFFERING:
                default:
                    textView.setText("Buffering");
                    break;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            Log.e(LOG_TAG, "Error during playback: ", throwable);
        }

        @Override
        public void onNewMetadataAvailable(Map<String, String> metadata) {

        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            exoService = ((ExoService.PlaybackBinder) service).getExoService();
            exoService.registerNewPlaybackStatusListener(playbackStatusListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            exoService = null;
        }
    };

}
