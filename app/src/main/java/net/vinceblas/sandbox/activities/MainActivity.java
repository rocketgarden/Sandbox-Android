package net.vinceblas.sandbox.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import net.vinceblas.sandbox.R;

import butterknife.Bind;
import butterknife.ButterKnife;

public class MainActivity extends Activity {

    @Bind(R.id.button) Button button1;
    @Bind(R.id.button2) Button button2;
    @Bind(R.id.button3) Button button3;
    @Bind(R.id.button4) Button button4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        button1.setEnabled(true);
        button1.setText("ExoPlayer Activity");
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ExoActivity.class));
            }
        });
        button2.setText("ExoPlayer Service");
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ExoServiceActivity.class));
            }
        });

        button3.setVisibility(View.GONE);
        button4.setVisibility(View.GONE);
    }
}
