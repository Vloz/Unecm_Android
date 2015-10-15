package fr.bobenrieth.unecm;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.LogRecord;

public class AutoSearchActivity extends AppCompatActivity {
    public final static String EXTRA_FILES = "fr.bobenrieth.unecm.FILES";
    private static final int MY_PERMISSIONS_REQUEST_STORAGE = 1;

    private DiscImageFileFinder finder = new DiscImageFileFinder();
    private final int interval = 1000; // 1 Second
    private Handler ellispsisHandler = new Handler();
    private Runnable ellipsis = new Runnable(){
        public void run() {
            String s = searchTitleTextview.getText().toString();
            String e = s.substring(s.length() - 3, s.length());
            if( e.equals("..."))
                searchTitleTextview.setText(s.substring(0,s.length()-3)+"   ");
            else
                searchTitleTextview.setText(s.substring(0, s.length() - 3) + e.replaceFirst("\\s", "."));

            String details = finder.getFiles_founds_count()+" "+getResources().getString(R.string.search_details_found)+"\n"
                    +finder.getDir_processed_count()+"/"+finder.getDir_total_count()+" "+getResources().getString(R.string.search_details_processed);
            detailsTextview.setText(details);
            if(finder.getDir_total_count() != finder.getDir_processed_count()-1)
                ellispsisHandler.postDelayed(ellipsis, interval);
            else{
                launchMain();
            }
        }
    };

    private TextView searchTitleTextview;
    private TextView detailsTextview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auto_search);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                || ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_STORAGE);
        else
            finder.findInDirectory(Environment.getExternalStorageDirectory());
        searchTitleTextview = (TextView) findViewById(R.id.search_title_textview);
        detailsTextview = (TextView) findViewById(R.id.search_details_textview);
        ellispsisHandler.postDelayed(ellipsis, interval);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    finder.findInDirectory(Environment.getExternalStorageDirectory());
                } else {
                    finish();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    void launchMain(){
        Intent intent = new Intent(this, MainActivity.class);
        ArrayList<String> files = finder.getFilesPath();
        intent.putStringArrayListExtra(EXTRA_FILES, files);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        System.runFinalizersOnExit(true);
    }
}
