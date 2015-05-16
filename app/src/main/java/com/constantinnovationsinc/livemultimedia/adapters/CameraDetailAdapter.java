/*
*   Copyright 2015 Constant Innovations Inc
*
*    Licensed under the Apache License, Version 2.0 (the "License");
*    you may not use this file except in compliance with the License.
*    You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS,
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*    See the License for the specific language governing permissions and
*    limitations under the License.
*/
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
    private ArrayList<String> mDataset = new ArrayList<>();

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
