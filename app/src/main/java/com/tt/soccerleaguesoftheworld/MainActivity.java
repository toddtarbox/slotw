package com.tt.soccerleaguesoftheworld;

import android.app.Activity;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new MainFragment())
                    .commit();
        }
    }

    public static class MainFragment extends Fragment implements AdapterView.OnItemSelectedListener {

        // TODO: The API_KEY really should be externalized and injected at build time to keep it somewhat
        // secret, but since this is just a demo app we won't worry about that now.
        private final String API_KEY = "am6e629dumycebwgmuqeb8rs";

        private final String ALL_LEAGUES_REQUEST = "http://api.espn.com/v1/sports/soccer?apikey=%s";
        private final String LEAGUE_TEAMS_REQUEST = "http://api.espn.com/v1/sports/soccer/%s/teams?apikey=%s";

        private Spinner mCountrySpinner;
        private Spinner mLeagueSpinner;
        private ListView mTeamListView;

        private int mSelectedCountryIndex = -1;
        private int mSelectedLeagueIndex = -1;
        private boolean mUpdateLeagues = true;
        private boolean mUpdateTeams = true;

        private ArrayAdapter<String> mCountryListAdapter;
        private ArrayAdapter<String> mLeagueListAdapter;
        private TeamArrayAdapter mTeamListAdapter;

        private HashMap<String, ArrayList<String>> mCountryLeaguesMap = new HashMap<String, ArrayList<String>>();
        private HashMap<String, String> mLeagueIDMap = new HashMap<String, String>();

        private ProgressDialog mProgressDialog;


        public MainFragment() {
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setRetainInstance(true);

            mProgressDialog = new ProgressDialog(getActivity());
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            mCountrySpinner = (Spinner)rootView.findViewById(R.id.country_spinner);
            mCountrySpinner.setOnItemSelectedListener(this);

            mLeagueSpinner = (Spinner)rootView.findViewById(R.id.league_spinner);
            mLeagueSpinner.setOnItemSelectedListener(this);

            mTeamListView = (ListView)rootView.findViewById(R.id.teams_list);

            View emptyView = rootView.findViewById(R.id.empty);
            mTeamListView.setEmptyView(emptyView);

            // Restore the adapters if necessary (ie. configuration change)
            if (mCountryListAdapter != null) {
                mCountrySpinner.setAdapter(mCountryListAdapter);
                mUpdateLeagues = false;
            } else {
                new FetchCountriesTask().execute();
            }

            if (mLeagueListAdapter != null) {
                mLeagueSpinner.setAdapter(mLeagueListAdapter);
                mUpdateTeams = false;
            }

            if (mTeamListAdapter != null) {
                mTeamListView.setAdapter(mTeamListAdapter);
            }

            return rootView;
        }

        /* OnItemSelectedListener methods */
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int index, long l) {
            if (adapterView.getId() == mCountrySpinner.getId()) {
                if (mUpdateLeagues) {
                    new UpdateLeaguesTask().execute(index);
                } else if (mSelectedCountryIndex >= 0) {
                    mCountrySpinner.setSelection(mSelectedCountryIndex);
                }
                mSelectedCountryIndex = index;
                mUpdateLeagues = true;
            } else if (adapterView.getId() == mLeagueSpinner.getId()) {
                if (mUpdateTeams) {
                    new UpdateTeamsTask().execute(index);
                } else if (mSelectedLeagueIndex >= 0) {
                    mLeagueSpinner.setSelection(mSelectedLeagueIndex);
                }

                mSelectedLeagueIndex = index;
                mUpdateTeams = true;
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
        }
        /* End OnItemSelectedListener methods */

        /* Helper Methods */
        private String getLeaguesUrl() {
            return String.format(ALL_LEAGUES_REQUEST, API_KEY);
        }

        private String getTeamsUrl(String leagueID) {
            return String.format(LEAGUE_TEAMS_REQUEST, leagueID, API_KEY);
        }
        /* End Helper Methods */

        /* AsyncTasks */
        // A background task that allows us to fetch all the leagues of the world
        private class FetchCountriesTask extends AsyncTask<Void, Void, ArrayList<String>> {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                showProgressDialog(R.string.loading_countries);
            }

            @Override
            protected ArrayList<String> doInBackground(Void... voids) {
                HttpClient client = new DefaultHttpClient();
                String url = getLeaguesUrl();
                Log.d("FetchCountriesTask", "Requesting countries: " + url);

                HttpGet request = new HttpGet(url);
                request.addHeader("Content-type", "application/json");

                ArrayList<String> countries = new ArrayList<String>();

                try {
                    String response = client.execute(request, new BasicResponseHandler());
                    JSONObject json = new JSONObject(response);

                    // The response will be an array, who's key is 'sports' with a single JSONObject.
                    // From that object we want the array who's key is 'leagues'.
                    JSONObject outerObject = json.getJSONArray("sports").getJSONObject(0);
                    JSONArray leaguesArray = outerObject.getJSONArray("leagues");

                    // Loop through each league found and extract those belonging to a specific country.
                    // We'll create a map of leagues to their respective countries.
                    for (int i = 0; i < leaguesArray.length(); i++) {
                        JSONObject league = leaguesArray.getJSONObject(i);

                        if (league.has("country")) {
                            String countryName = league.getJSONObject("country").getString("name");
                            ArrayList<String> countryLeagues;

                            if (!mCountryLeaguesMap.containsKey(countryName)) {
                                // Haven't seen this country yet, so add it to the list and
                                // create a new array for the map.
                                countries.add(countryName);
                                countryLeagues = new ArrayList<String>();
                            } else {
                                // Not a new country, so get its array from the map.
                                countryLeagues = mCountryLeaguesMap.get(countryName);
                            }

                            // Add the league to the country's map array
                            String leagueName = league.getString("name");
                            String leagueShortName = league.getString("shortName");
                            String leagueDisplay = String.format("%s (%s)", leagueName, leagueShortName);
                            countryLeagues.add(leagueDisplay);
                            mCountryLeaguesMap.put(countryName, countryLeagues);

                            // Add the league ID to the map
                            mLeagueIDMap.put(leagueDisplay, league.getString("abbreviation"));
                        }
                    }

                } catch (Exception ex) {
                    Log.e("MainActivity", ex.getMessage(), ex);
                }

                return countries;
            }

            @Override
            protected void onPostExecute(ArrayList<String> countries) {
                super.onPostExecute(countries);

                // Sort the countries ascending by country name
                Collections.sort(countries);

                cancelProgressDialog();

                mCountryListAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_dropdown_item, countries);
                mCountrySpinner.setAdapter(mCountryListAdapter);
            }
        }

        // A background task that allows us to compile the list of leagues based on the selected country
        private class UpdateLeaguesTask extends AsyncTask<Integer, Void, ArrayList<String>> {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                mLeagueListAdapter = null;
                mLeagueSpinner.setAdapter(mLeagueListAdapter);
            }

            @Override
            protected ArrayList<String> doInBackground(Integer... params) {
                int countryIndex = params[0];
                String countryName = mCountryListAdapter.getItem(countryIndex);

                Log.d("UpdateLeaguesTasks", "Fetching cached leagues for : " + countryName);
                return mCountryLeaguesMap.get(countryName);
            }

            @Override
            protected void onPostExecute(ArrayList<String> leagues) {
                super.onPostExecute(leagues);

                // Sort the leagues ascending by league name
                Collections.sort(leagues);

                mLeagueListAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_dropdown_item, leagues);
                mLeagueSpinner.setAdapter(mLeagueListAdapter);
            }
        }

        // A background task that allows us to fetch the list of teams for the selected country/league
        private class UpdateTeamsTask extends AsyncTask<Integer, Void, ArrayList<JSONObject>> {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                showProgressDialog(R.string.loading_teams);

                mTeamListAdapter = null;
                mTeamListView.setAdapter(mTeamListAdapter);
            }

            @Override
            protected ArrayList<JSONObject> doInBackground(Integer... params) {
                int leagueIndex = params[0];
                String leagueName = mLeagueListAdapter.getItem(leagueIndex);
                String leagueID = mLeagueIDMap.get(leagueName);

                HttpClient client = new DefaultHttpClient();
                String url = getTeamsUrl(leagueID);
                Log.d("UpdateTeamsTask", "Requesting teams: " + url);

                HttpGet request = new HttpGet(url);
                request.addHeader("Content-type", "application/json");

                ArrayList<JSONObject> teams = new ArrayList<JSONObject>();

                try {
                    String response = client.execute(request, new TeamsResponseHandler());
                    if (!response.isEmpty()) {
                        JSONObject json = new JSONObject(response);

                        // The response will be an array, who's key is 'sports' with a single JSONObject.
                        // From that object we want the single JSONObject from the array with key 'leagues'.
                        // Then from object we want the array who's key is 'teams'.
                        JSONObject outerObject = json.getJSONArray("sports").getJSONObject(0);
                        JSONObject leagueObject = outerObject.getJSONArray("leagues").getJSONObject(0);
                        JSONArray teamsArray = leagueObject.getJSONArray("teams");

                        for (int i=0; i<teamsArray.length(); i++) {
                            JSONObject team = teamsArray.getJSONObject(i);
                            teams.add(team);
                        }
                    }
                } catch (Exception ex) {
                    Log.e("MainActivity", ex.getMessage(), ex);
                }

                return teams;
            }

            @Override
            protected void onPostExecute(ArrayList<JSONObject> teams) {
                super.onPostExecute(teams);

                // Sort the teams ascending by team name
                Collections.sort(teams, new Comparator<JSONObject>() {
                    @Override
                    public int compare(JSONObject jsonObject, JSONObject jsonObject2) {
                        try {
                            return jsonObject.getString("name").compareToIgnoreCase(jsonObject2.getString("name"));
                        } catch (JSONException ex) {
                            return 0;
                        }
                    }
                });

                cancelProgressDialog();

                mTeamListAdapter = new TeamArrayAdapter(getActivity(), teams);
                mTeamListView.setAdapter(mTeamListAdapter);
            }
        }
        /* End AsyncTasks */

        private class TeamsResponseHandler extends BasicResponseHandler {

            @Override
            public String handleResponse(HttpResponse response) throws HttpResponseException, IOException {

                try {
                    return super.handleResponse(response);
                } catch (Exception ex) {
                    // Some leagues just don't load like the others do. To prevent errors in the logs, we'll just
                    // return empty so no teams are listed.
                    Log.d("ResponseHandler", "Unable to load teams: " + ex.getMessage());
                    return "";
                }
            }
        }

        private void showProgressDialog(int messageId) {
            final String message = getString(messageId);
            mProgressDialog.setMessage(message);
            mProgressDialog.show();
        }

        private void cancelProgressDialog() {
            mProgressDialog.cancel();
        }
    }
}
