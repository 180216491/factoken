package com.blockchain.token.error;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.blockchain.token.R;
import com.blockchain.token.error.Model.ErrorModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;


/**
 * Created on 2017/8/23.
 * 错误日志
 */

public class ErrorListActivity extends Activity {

    ListView listView;

    ErrorAdapter adapter;

    List<ErrorModel> files = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error);
        listView = (ListView) findViewById(R.id.listView);
        File dir = getFilesDir();
        File[] listFiles = dir.listFiles();
        for(File file: listFiles){
            if (file.getName().contains("crash-")){
                ErrorModel model = new ErrorModel();
                model.name = file.getName();
                model.path = file.getPath();
                files.add(model);
            }
        }
        adapter = new ErrorAdapter(this, files);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ErrorModel model = files.get(position);
                Intent intent = new Intent(ErrorListActivity.this, ErrorDeatilsActivity.class);
                intent.putExtra("ErrorModel", model);
                startActivity(intent);
            }
        });
    }

    public class ErrorAdapter extends BaseAdapter {
        protected LayoutInflater mInflater;
        protected List<ErrorModel> data = new ArrayList<>();

        public ErrorAdapter(Context context, List<ErrorModel> data) {
            this.data = data;
            mInflater =  LayoutInflater.from(context);
        }

        public int getCount() {
            return this.data.size();
        }

        public ErrorModel getItem(int position) {
            return position >= this.data.size() ? null : this.data.get(position);
        }

        public long getItemId(int position) {
            return (long) position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = mInflater.inflate(R.layout.listitem_error, null);
                holder.error_item = (TextView) convertView.findViewById(R.id.error_item);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder)convertView.getTag();
            }
            ErrorModel model = this.data.get(position);
            holder.error_item.setText(model.name);
            return convertView;
        }

        public class ViewHolder{
            TextView error_item;
        }
    }
}
