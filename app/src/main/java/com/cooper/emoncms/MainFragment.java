package com.cooper.emoncms;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.XAxisValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MainFragment extends Fragment
{
    static final String TAG = "emoncms";
    static String emoncmsURL;
    static String emoncmsAPIKEY;
    static String emoncmsProtocol;
    TextView txtPower;
    TextView txtUseToday;
    TextView txtDebug;
    LineChart chart1;
    BarChart chart2;
    boolean blnDebugOnShow = false;
    Handler mHandler = new Handler();
    Float yesterdaysPowerUsage = 0F;
    Float totalPowerUsage = 0F;
    int todaysDate = 0;

    int wattFeedId = 0;
    int kWhFeelId = 0;

    boolean startDynamicGet = false;

    private Runnable mDaysofWeekRunner = new Runnable()
    {
        @Override
        public void run()
        {
            Date now = new Date();
            int n = now.getTimezoneOffset();
            int offset = n / -60;
            long timenow = now.getTime();
            int interval = 3600 * 24;
            long timenow_s = timenow / 1000;
            long endTime = ((Double) Math.floor(timenow_s / interval)).longValue() * interval;
            long startTime = endTime - interval * 6;
            startTime -= offset * 3600;
            endTime -= offset * 3600;
            startTime *= 1000;
            endTime *= 1000;

            final long chart2EndTime = endTime;//endTime * 1000;
            final long chart2StartTime = startTime;//startTime * 1000;

            String url = String.format("%s%s/feed/data.json?id=%d&start=%d&end=%d&interval=86400&skipmissing=1&limitinterval=1&apikey=%s", emoncmsProtocol, emoncmsURL, kWhFeelId, chart2StartTime, chart2EndTime, emoncmsAPIKEY);
            Log.i("EMONCMS", url);
            JsonArrayRequest jsArrayRequest = new JsonArrayRequest
                    (url, new Response.Listener<JSONArray>()
                    {

                        @Override
                        public void onResponse(JSONArray response)
                        {
                            ArrayList<BarEntry> entries = new ArrayList<>();
                            ArrayList<String> labels = new ArrayList<>();
                            SimpleDateFormat sdf = new SimpleDateFormat("E");

                            List<Long> dates = new ArrayList<>();
                            List<Float> power = new ArrayList<>();

                            for (int i = 0; i < response.length(); i++)
                            {
                                JSONArray row;

                                try
                                {
                                    row = response.getJSONArray(i);
                                    Long date = row.getLong(0);
                                    if (date <= chart2EndTime)
                                    {
                                        dates.add(date);
                                        power.add(((Double) row.getDouble(1)).floatValue());
                                    }
                                }
                                catch (JSONException e)
                                {
                                    e.printStackTrace();
                                }
                            }

                            for (int i = 0; i < power.size() - 1; i++)
                            {
                                labels.add(sdf.format(new Date(dates.get(i))).substring(0, 1));
                                entries.add(new BarEntry(power.get(i + 1) - power.get(i), i));
                            }

                            if (power.size() > 0)
                            {
                                yesterdaysPowerUsage = power.get(power.size() - 1);
                                labels.add(sdf.format(new Date(dates.get(dates.size() - 1))).substring(0, 1));
                                entries.add(new BarEntry(0, entries.size()));
                            }

                            try
                            {
                                BarDataSet dataset = new BarDataSet(entries, "kWh");
                                dataset.setColor(Color.parseColor("#3399FF"));
                                dataset.setValueTextColor(Color.parseColor("#cccccc"));
                                dataset.setValueTextSize(getResources().getDimension(R.dimen.chartValueTextSize));
                                BarData barData = new BarData(labels, dataset);
                                chart2.setData(barData);
                                chart2.invalidate();
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }

                            if (blnDebugOnShow)
                            {
                                blnDebugOnShow = false;
                                txtDebug.setVisibility(View.GONE);
                            }

                            mHandler.post(mGetFeedsRunner);
                            startDynamicGet = true;
                        }
                    }, new Response.ErrorListener()
                    {

                        @Override
                        public void onErrorResponse(VolleyError error)
                        {
                            blnDebugOnShow = true;
                            txtDebug.setText("CONNECTION ERROR");
                            txtDebug.setVisibility(View.VISIBLE);
                            mHandler.postDelayed(mDaysofWeekRunner, 5000);
                        }
                    });

            jsArrayRequest.setTag(TAG);
            HTTPClient.getInstance(getActivity()).addToRequestQueue(jsArrayRequest);
        }
    };

    private Runnable mGetFeedsRunner = new Runnable()
    {
        @Override
        public void run()
        {
            String url = String.format("%s%s/feed/list.json?apikey=%s", emoncmsProtocol, emoncmsURL, emoncmsAPIKEY);

            JsonArrayRequest jsArrayRequest = new JsonArrayRequest
                    (url, new Response.Listener<JSONArray>()
                    {

                        @Override
                        public void onResponse(JSONArray response)
                        {
                            for (int i = 0; i < response.length(); i++)
                            {
                                JSONObject row;
                                try
                                {
                                    row = response.getJSONObject(i);
                                    int id = row.getInt("id");
                                    if (id == wattFeedId)
                                    {
                                        Float power = Float.parseFloat(row.getString("value"));
                                        txtPower.setText(String.format("%.0fW", power));
                                    }
                                    else if (id == kWhFeelId)
                                    {
                                        totalPowerUsage = ((Double) row.getDouble("value")).floatValue();
                                    }

                                }
                                catch (JSONException e)
                                {
                                    e.printStackTrace();
                                }
                            }

                            if (yesterdaysPowerUsage > 0)
                            {
                                Float todaysPowerUsage = totalPowerUsage - yesterdaysPowerUsage;

                                BarData data = chart2.getData();
                                BarDataSet set = data.getDataSetByIndex(0);

                                Entry e = set.getEntryForXIndex(set.getEntryCount() - 1);
                                e.setVal(todaysPowerUsage);

                                chart2.notifyDataSetChanged();
                                chart2.invalidate();
                                txtUseToday.setText(String.format("%.1fkWh", todaysPowerUsage));
                            }

                            if (blnDebugOnShow)
                            {
                                blnDebugOnShow = false;
                                txtDebug.setVisibility(View.GONE);
                            }

                            mHandler.post(mGetPowerHistoryRunner);
                        }
                    }, new Response.ErrorListener()
                    {

                        @Override
                        public void onErrorResponse(VolleyError error)
                        {
                            blnDebugOnShow = true;
                            txtDebug.setText("CONNECTION ERROR");
                            txtDebug.setVisibility(View.VISIBLE);
                            mHandler.postDelayed(mGetFeedsRunner, 5000);
                        }
                    });

            jsArrayRequest.setTag(TAG);
            HTTPClient.getInstance(getActivity()).addToRequestQueue(jsArrayRequest);
        }
    };


    private Runnable mGetPowerHistoryRunner = new Runnable()
    {
        @Override
        public void run()
        {
            Calendar cal = Calendar.getInstance();
            long endTime = cal.getTimeInMillis();
            cal.add(Calendar.HOUR, -6);
            long startTime = cal.getTimeInMillis();
            final int graph_interval = 15;

            String url = String.format("%s%s/feed/data.json?id=%d&start=%d&end=%d&interval=%d&skipmissing=1&limitinterval=1&apikey=%s", emoncmsProtocol, emoncmsURL, wattFeedId, startTime, endTime,graph_interval, emoncmsAPIKEY);
            Log.i("EMONCMS", url);
            JsonArrayRequest jsArrayRequest = new JsonArrayRequest(url, new Response.Listener<JSONArray>()
            {
                @Override
                public void onResponse(JSONArray response)
                {
                    LineData data = chart1.getData();
                    LineDataSet set = data.getDataSetByIndex(0);

                    long lastEntry = 0;

                    if (set == null)
                    {
                        set = new LineDataSet(null, "watts");
                        set.setColor(Color.parseColor("#3399FF"));
                        set.setValueTextColor(Color.parseColor("#cccccc"));
                        set.setValueTextSize(getResources().getDimension(R.dimen.chartValueTextSize));
                        set.setDrawCircles(false);
                        set.setDrawFilled(true);
                        set.setFillColor(Color.parseColor("#0699fa"));
                        set.setDrawValues(false);
                        set.setHighlightEnabled(false);
                        data.addDataSet(set);
                    }
                    else if (data.getXValCount() > 0)
                    {
                        lastEntry = Long.parseLong(data.getXVals().get(data.getXValCount()-1));
                    }

                    for (int i = 0; i < response.length(); i++)
                    {
                        JSONArray row;
                        try
                        {
                            row = response.getJSONArray(i);
                            long time = Long.parseLong(row.getString(0));

                            if (lastEntry == 0)
                            {
                                data.addXValue(row.getString(0));
                                data.addEntry(new Entry(Float.parseFloat(row.getString(1)), set.getEntryCount()), 0);
                            }
                            else if (time > (lastEntry+(graph_interval*1000)))
                            {
                                Entry e = set.getEntryForXIndex(0);
                                Boolean removeEntry = data.removeEntry(e, 0);

                                if (removeEntry)
                                {
                                    data.removeXValue(0);
                                    for (Entry entry : set.getYVals()) {
                                        entry.setXIndex(entry.getXIndex() - 1);
                                    }
                                }

                                data.addXValue(row.getString(0));
                                data.addEntry(new Entry(Float.parseFloat(row.getString(1)), set.getEntryCount()), 0);
                            }
                        }
                        catch (JSONException e)
                        {
                            e.printStackTrace();
                        }
                    }

                    chart1.notifyDataSetChanged();
                    chart1.invalidate();

                    if (blnDebugOnShow)
                    {
                        blnDebugOnShow = false;
                        txtDebug.setVisibility(View.GONE);
                    }

                    if (Calendar.getInstance().get(Calendar.DAY_OF_WEEK_IN_MONTH) != todaysDate)
                    {
                        todaysDate = Calendar.getInstance().get(Calendar.DAY_OF_WEEK_IN_MONTH);
                        mHandler.postDelayed(mDaysofWeekRunner, 10000);
                    } else
                        mHandler.postDelayed(mGetFeedsRunner, 10000);
                }
            }, new Response.ErrorListener()
            {

                @Override
                public void onErrorResponse(VolleyError error)
                {
                    blnDebugOnShow = true;
                    txtDebug.setText("CONNECTION ERROR");
                    txtDebug.setVisibility(View.VISIBLE);
                    mHandler.postDelayed(mGetPowerHistoryRunner, 5000);
                }
            });

            jsArrayRequest.setTag(TAG);
            HTTPClient.getInstance(getActivity()).addToRequestQueue(jsArrayRequest);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());
        emoncmsURL = SP.getString("emoncms_url", "emoncms.org");
        emoncmsAPIKEY = SP.getString("emoncms_apikey", "");
        wattFeedId = Integer.valueOf(SP.getString("emoncms_power_feed", "0"));
        kWhFeelId = Integer.valueOf(SP.getString("emoncms_kwh_feed", "0"));
        emoncmsProtocol = SP.getBoolean("emoncms_usessl", false) ? "https://" : "http://";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savesInstanceState)
    {
        super.onActivityCreated(savesInstanceState);

        View view = getView();

        txtPower = (TextView) view.findViewById(R.id.txtPower);
        txtUseToday = (TextView) view.findViewById(R.id.txtUseToday);
        txtDebug = (TextView) view.findViewById(R.id.txtDebug);

        chart1 = (LineChart) view.findViewById(R.id.chart1);
        chart1.setDrawGridBackground(false);
        chart1.getLegend().setEnabled(false);
        chart1.getAxisRight().setEnabled(false);
        chart1.setDescription("");
        chart1.setNoDataText("");
        chart1.setHardwareAccelerationEnabled(true);
        chart1.setData(new LineData());

        YAxis yAxis = chart1.getAxisLeft();
        yAxis.setEnabled(true);
        yAxis.setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART);
        yAxis.setDrawTopYLabelEntry(false);
        yAxis.setDrawGridLines(false);
        yAxis.setDrawAxisLine(false);
        yAxis.setTextColor(Color.parseColor("#cccccc"));

        XAxis xAxis = chart1.getXAxis();
        xAxis.setDrawAxisLine(false);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#cccccc"));
        xAxis.setValueFormatter(new MyCustomXAxisValueFormatter());

        chart2 = (BarChart) view.findViewById(R.id.chart2);
        chart2.setDrawGridBackground(false);
        chart2.getLegend().setEnabled(false);
        chart2.getAxisLeft().setEnabled(false);
        chart2.getAxisRight().setEnabled(false);
        chart2.setHardwareAccelerationEnabled(true);
        chart2.setDrawValueAboveBar(false);
        chart2.setDescription("");
        chart2.setNoDataText("");
        chart2.setTouchEnabled(false);

        xAxis = chart2.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM_INSIDE);
        xAxis.setTextColor(Color.parseColor("#cccccc"));
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextSize(getResources().getDimension(R.dimen.chartValueTextSize));
    }

    @Override
    public void onResume()
    {
        super.onResume();
        int day_of_month = Calendar.getInstance().get(Calendar.DAY_OF_WEEK_IN_MONTH);
        if (day_of_month != todaysDate)
        {
            todaysDate = Calendar.getInstance().get(Calendar.DAY_OF_WEEK_IN_MONTH);
            startDynamicGet = false;
        }

        if (!emoncmsURL.equals("") && !emoncmsAPIKEY.equals(""))
        {
            if (startDynamicGet)
                mHandler.post(mGetFeedsRunner);
            else
                mHandler.post(mDaysofWeekRunner);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        HTTPClient.getInstance(getActivity()).cancellAll(TAG);
        mHandler.removeCallbacks(mGetFeedsRunner);
    }

    public class MyCustomXAxisValueFormatter implements XAxisValueFormatter
    {
        @Override
        public String getXValue(String original, int index, ViewPortHandler viewPortHandler)
        {
            DateFormat df = new SimpleDateFormat("hh:mm:ss");
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(Long.parseLong(original));
            return (df.format(cal.getTime()));
        }
    }
}