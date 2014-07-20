package com.tt.soccerleaguesoftheworld;


import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.provider.Browser;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


public class TeamArrayAdapter extends ArrayAdapter<JSONObject> implements View.OnClickListener {

    private LayoutInflater mLayoutInflater;

    public TeamArrayAdapter(Context context, ArrayList<JSONObject> teams) {
        super(context, R.layout.team_list_item, teams);

        mLayoutInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        JSONObject teamJSON = getItem(position);

        ViewHolder holder;

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.team_list_item, parent, false);

            holder = new ViewHolder();
            holder.nameView = (TextView)convertView.findViewById(R.id.team_name);
            holder.nicknameView = (TextView)convertView.findViewById(R.id.team_nickname);
            holder.locationView = (TextView)convertView.findViewById(R.id.team_location);

            holder.urlView = (TextView)convertView.findViewById(R.id.team_url);
            holder.urlView.setOnClickListener(this);
            SpannableString spannable = new SpannableString(getContext().getString(R.string.view_on_espn));
            spannable.setSpan(new ForegroundColorSpan(getContext().getResources().getColor(android.R.color.holo_blue_light)), 0, spannable.length(), 0);
            holder.urlView.setText(spannable);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder)convertView.getTag();
        }

        try {
            int color = Color.BLACK;
            if (teamJSON.has("color")) {
                String teamColor = teamJSON.getString("color");
                try {
                    color = Color.parseColor(teamColor);
                } catch (IllegalArgumentException ex) {} // handle invalid colors
            }

            holder.nameView.setText(String.format(getContext().getString(R.string.team_name), teamJSON.getString("name")));
            holder.nameView.setTextColor(color);

            holder.nicknameView.setText(String.format(getContext().getString(R.string.team_nickname), teamJSON.getString("nickname")));
            holder.locationView.setText(String.format(getContext().getString(R.string.team_location), teamJSON.getString("location")));

            String url = teamJSON.getJSONObject("links").getJSONObject("web").getJSONObject("teams").getString("href");
            holder.urlView.setTag(url);
        } catch (JSONException ex) { }

        return convertView;
    }

    @Override
    public void onClick(View view) {
        String url = (String)view.getTag();

        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        browserIntent.putExtra(Browser.EXTRA_APPLICATION_ID, getContext().getPackageName());
        getContext().startActivity(browserIntent);
    }

    // This class improves list item view creation performance by reducing the number of calls
    // the find a view in the inflated layout. As list item views are recycled, this class will
    // provide direct access to the already located sub-views.
    private static class ViewHolder {
        TextView nameView;
        TextView nicknameView;
        TextView locationView;
        TextView urlView;
    }
}
