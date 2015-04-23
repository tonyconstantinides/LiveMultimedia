package com.constantinnovationsinc.livemultimedia.adapters;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import com.constantinnovationsinc.livemultimedia.R;

import java.util.ArrayList;

public class CameraDetailAdapter extends ArrayAdapter<String> {
    private Context mContext;
    private ArrayList<String> mDataset = new ArrayList<String>();

    // Provide a suitable constructor (depends on the kind of dataset)
    public  CameraDetailAdapter(Context context, ArrayList<String> items) {
        super(context, 0, items);
        mContext = context;
        mDataset = items;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.fragment_camera_detail, null);
        }
        return view;
    }

    public static class ViewHolder  {
        public TextView mTextView;
        public ViewHolder(TextView v) {
            mTextView = v;
        }
    }
}
