package fr.bobenrieth.unecm;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private  static HashSet<String> inputBeingProcessed = new HashSet<String>();

    private static MainActivity singleton;

    private boolean keepEcmFiles = false;


    public native String unecm(String inputFullPath);

    private FloatingActionButton fab;
    private ListView fileListView;
    FilesAdapter filesAdapter;
    private String selectedFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //*** used for ndk threads ***//
        singleton = this;
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        fileListView =  (ListView) findViewById(R.id.files_listView);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.hide();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                fab.hide();
                //check if output exists
                final File output = new File(selectedFile.substring(0, selectedFile.length() - 4));
                if (output.exists()) {
                    //Dialog for overwrite
                    new AlertDialog.Builder(view.getContext())
                            .setTitle(getResources().getString(R.string.output_exists_title))
                            .setMessage(getResources().getString(R.string.output_exists_body))
                            .setIcon(getResources().getDrawable(R.drawable.ic_info_outline))
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    if (output.delete())
                                        startTask(selectedFile);
                                    else
                                        showGeneralError(getResources().getString(R.string.cannot_delete_output));

                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    fab.show();
                                }
                            }).show();

                } else
                    startTask(selectedFile);
            }
        });
        onNewIntent(getIntent());
        fileListView.setOnItemClickListener(
                new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        File selItem = (File) filesAdapter.getItem(position);
                        selectedFile = selItem.getAbsolutePath();
                        if (!selItem.exists() || inputBeingProcessed.contains(selItem.getAbsolutePath()))
                            fab.hide();
                        else
                            fab.show();
                    }
                });
    }

/*    private void loadListView(){
        fileListView.setVisibility(View.INVISIBLE);
        Intent intent = getIntent();

        fileListView.setVisibility(View.VISIBLE);
    }*/

    private void startTask(String inputPath){
        int position = filesAdapter.getPosition(new File(inputPath));
        View v = fileListView.getChildAt(position);
        ProgressBar bar = (ProgressBar) v.findViewById(R.id.file_progressbar);
        TextView detailsTv = (TextView) v.findViewById(R.id.percent_textview);
        TextView outputTv = (TextView) v.findViewById(R.id.details_textview);
        bar.setVisibility(View.VISIBLE);
        bar.setProgress(0);
        outputTv.setVisibility(View.GONE);
        outputTv.setTextColor(getResources().getColorStateList(R.color.secondary_textcolor_selector));
        detailsTv.setText("0%");
        if(!inputBeingProcessed.contains(inputPath))
            inputBeingProcessed.add(inputPath);
        unecm(inputPath);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        ArrayList<File> files = new ArrayList<File>();
        for(String path : intent.getStringArrayListExtra(AutoSearchActivity.EXTRA_FILES))
            files.add(new File(path));
        if(filesAdapter==null){
            filesAdapter = new FilesAdapter(this, files);
            fileListView.setAdapter(filesAdapter);
        }
        else{
            filesAdapter.clear();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
                    filesAdapter.addAll(files);
            else
                for(File f : files)
                    filesAdapter.add(f);

        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            Intent intent = new Intent(this, AutoSearchActivity.class);
            startActivity(intent);
            return true;
        }
        if(id == R.id.action_keep_ecm)
        {
            item.setChecked(!item.isChecked());
            keepEcmFiles = item.isChecked();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static void messageFromJni(String inputPath,int action,int intValue, String strValue ){
        switch (action){
            //UPDATE_PROGRESS
            case 0:
                singleton.updateProgress(inputPath, intValue);
                break;
            //UPDATE_STATE
            case 1:
                singleton.updateStatus(inputPath,intValue,strValue);
                break;
        }
    }

    public void updateStatus(final String inputPath, final int state, final String details){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                File input = new File(inputPath);
                int position = singleton.filesAdapter.getPosition(input);
                if(position==-1)
                    return;
                View v = singleton.fileListView.getChildAt(position);
                LinearLayout layout = (LinearLayout) v.findViewById(R.id.item_main_linear);
                ProgressBar bar = (ProgressBar) v.findViewById(R.id.file_progressbar);
                TextView detailsTv = (TextView) v.findViewById(R.id.percent_textview);
                TextView outputTv = (TextView) v.findViewById(R.id.details_textview);
                switch (state) {
                    //OK
                    case 0:
                        inputBeingProcessed.remove(inputPath);
                        bar.setVisibility(View.GONE);
                        outputTv.setVisibility(View.VISIBLE);
                        outputTv.setTextColor(getResources().getColorStateList(R.color.green_secondary_textcolor_selector));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            layout.setBackgroundTintList(getResources().getColorStateList(R.color.activated_green_background_selector));
                        outputTv.setText(R.string.details_done);
                        detailsTv.setText("100%");
                        if(!keepEcmFiles)
                            input.delete();
                        break;
                    //ERROR
                    case 1:
                        inputBeingProcessed.remove(inputPath);
                        File output = new File(inputPath.substring(0, inputPath.length() - 4));
                        if(output.exists())
                            output.delete();
                        bar.setVisibility(View.GONE);
                        outputTv.setVisibility(View.VISIBLE);
                        outputTv.setTextColor(getResources().getColorStateList(R.color.red_secondary_textcolor_selector));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                            layout.setBackgroundTintList(getResources().getColorStateList(R.color.activated_red_background_selector));

                        outputTv.setText(details);
                        break;
                }
            }
        });

    }

    public void updateProgress(final String input, final int percent ){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(!inputBeingProcessed.contains(input)) //In case of the activity is recreated and a back task still runing
                    inputBeingProcessed.add(input);
                int position = singleton.filesAdapter.getPosition(new File(input));
                if(position==-1)
                    return;
                View v = singleton.fileListView.getChildAt(position);
                ProgressBar bar = (ProgressBar) v.findViewById(R.id.file_progressbar);
                bar.setVisibility(View.VISIBLE);
                TextView detailsTV = (TextView) v.findViewById(R.id.details_textview);
                detailsTV.setVisibility(View.GONE);
                TextView percentTv = (TextView) v.findViewById(R.id.percent_textview);
                String str= Integer.toString(percent)+"%";

                percentTv.setText(str);
                bar.setProgress(percent);
            }
        });
    }

    private void showGeneralError(String error){
        LayoutInflater inflater = getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast_error,
                (ViewGroup) findViewById(R.id.toast_layout_root));

        TextView text = (TextView) layout.findViewById(R.id.text);
        text.setText(error);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();

        View empty = findViewById(R.id.empty);
        ListView list = (ListView) findViewById(R.id.files_listView);
        list.setEmptyView(empty);
    }

    static {
        System.loadLibrary("unecm");
    }
}


