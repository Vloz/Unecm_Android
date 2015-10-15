package fr.bobenrieth.unecm;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by vloz on 06/10/2015.
 */
public class FilesAdapter extends ArrayAdapter<File> {
    public FilesAdapter(Context context, ArrayList<File> Files) {
        super(context, 0, Files);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        File file = getItem(position);
        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_file, parent, false);
        }

        TextView fileName = (TextView) convertView.findViewById(R.id.fileName);
        TextView percentTextview = (TextView) convertView.findViewById(R.id.percent_textview);
        TextView detailsTextview = (TextView) convertView.findViewById(R.id.details_textview);
        ProgressBar bar = (ProgressBar) convertView.findViewById(R.id.file_progressbar);

        fileName.setText(file.getName());
        String output_path = file.getPath().substring(0, (int)file.getPath().length()-4);
        File output = new File(output_path);
        percentTextview.setText(humanReadableByteCount(file.length(), true));
        if(output.exists())
        {
            detailsTextview.setTextColor(convertView.getResources().getColorStateList(R.color.orange_secondary_textcolor_selector));
            detailsTextview.setText(convertView.getResources().getString(R.string.details_exists));
        }
        else {
            detailsTextview.setTextColor(convertView.getResources().getColorStateList(R.color.secondary_textcolor_selector));
            detailsTextview.setText(convertView.getResources().getString(R.string.details_ready));
        }


        // Return the completed view to render on screen
        return convertView;
    }

    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}