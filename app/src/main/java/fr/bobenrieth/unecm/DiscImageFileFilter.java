package fr.bobenrieth.unecm;

import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;

public class DiscImageFileFilter implements FileFilter {

    protected static final String TAG = "DiscImageFileFilter";


    @Override
    public boolean accept(File f) {
        if ( f.isHidden() || !f.canRead() )
            return false;
        else
            return f.isDirectory() || checkFileExtension(f);
    }

    private boolean checkFileExtension( File f ) {
        String ext = getFileExtension(f);
        if ( ext == null) return false;
        try {
            if ( SupportedFileFormat.valueOf(ext.toUpperCase()) != null ) {
                return true;
            }
        } catch(IllegalArgumentException e) {
            //Not known enum value
            return false;
        }
        return false;
    }

    private boolean checkFileExtension( String fileName ) {
        String ext = getFileExtension(fileName);
        if ( ext == null) return false;
        try {
            if ( SupportedFileFormat.valueOf(ext.toUpperCase()) != null ) {
                return true;
            }
        } catch(IllegalArgumentException e) {
            //Not known enum value
            return false;
        }
        return false;
    }

    public String getFileExtension( File f ) {
        return getFileExtension( f.getName() );
    }

    public String getFileExtension( String fileName ) {
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i+1);
        } else
            return null;
    }

    /**
     * Files formats currently supported by Library
     */
    public enum SupportedFileFormat
    {
        ECM("ecm")
       ;

        private String filesuffix;

        SupportedFileFormat( String filesuffix ) {
            this.filesuffix = filesuffix;
        }

        public String getFilesuffix() {
            return filesuffix;
        }
    }

}
