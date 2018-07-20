package com.blockchain.token.error;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.blockchain.token.R;
import com.blockchain.token.error.Model.ErrorModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;


/**
 * Created on 2017/8/23.
 * 错误日志
 */

public class ErrorDeatilsActivity extends Activity {

    TextView error_details;
    Button share;
    StringBuilder builder = new StringBuilder();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error_deatils);
        error_details = (TextView) findViewById(R.id.error_details);
        share = (Button) findViewById(R.id.share);
        Intent intent = getIntent();
        ErrorModel model = (ErrorModel)intent.getSerializableExtra("ErrorModel");
        File file = new File(model.path);
        try {

            FileInputStream stream = new FileInputStream(file);
            InputStreamReader reader = new InputStreamReader(stream, "UTF-8");
            BufferedReader br = new BufferedReader(reader);
            String line;
            while((line = br.readLine()) != null)
            {
                builder.append(line);
            }
            error_details.setText(builder.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareText();
            }
        });
    }

    public void shareText() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, builder.toString());
        intent.setType("text/plain");
        startActivity(Intent.createChooser(intent,"share"));

    }
}
