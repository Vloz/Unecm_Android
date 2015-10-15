package fr.bobenrieth.unecm;

import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

public class DiscImageFileFinder {
    private int files_founds_count = 0;
    private int dir_processed_count = 0;
    private int dir_total_count = 0;
    private Thread thread;
    private ArrayList<File> files = new ArrayList<File>();

    public void findInDirectory(final File searchRoot){
        boolean b = searchRoot.canRead();
        initTotalDirCount(searchRoot);
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                File dir = null;
                boolean b1 = searchRoot.canRead();
                files.add(searchRoot);
                do{
                    dir = getNextDirectory(files);
                    if(dir == null)
                        break;
                    File[] result = dir.listFiles(new DiscImageFileFilter());
                    for(File f : result)
                        if(!f.isDirectory())
                            files_founds_count++;
                    files.remove(dir);
                    dir_processed_count++;
                    dir = null;
                    if (result != null) //happens when dir is readOnly
                        files.addAll(new ArrayList<File>(Arrays.asList(result)));

                }while (getNextDirectory(files) != null);
            }
        });
        thread.start();
    }

    private void initTotalDirCount(File searchRoot){
        File[] files = searchRoot.listFiles();
        if (files != null)
            for (File file : files) {
                if (file.isDirectory() && file.canRead() && !file.isHidden()) {
                    dir_total_count++;
                    initTotalDirCount(file);
                }
            }
    }



    private static File getNextDirectory(ArrayList<File> files){
        for(File f : files)
            if(f.isDirectory() && f.canRead())
                return f;
        return null;
    }

    public int getDir_total_count() {
        return dir_total_count;
    }

    public int getDir_processed_count() {
        return dir_processed_count;
    }

    public int getFiles_founds_count() {
        return files_founds_count;
    }

    public ArrayList<String> getFilesPath(){
        ArrayList<String> r = new ArrayList<String>();
        for(File f : files) //Remove directory from it (if the search task was aborted)
            if(!f.isDirectory())
                r.add(f.getAbsolutePath());
        return r;
    }
}


