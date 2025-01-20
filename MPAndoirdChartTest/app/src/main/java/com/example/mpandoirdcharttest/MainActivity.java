package com.example.mpandoirdcharttest;

import java.text.DateFormat;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import com.github.mikephil.charting.utils.MPPointF;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

//-------------------------------------------------------------------------------------------------
// SensorInfo -
//-------------------------------------------------------------------------------------------------
class SensorInfo {
    public Double temperature, pressure, humidity, rssi, battery;
    public String utc_timestamp, mac;
}

//-------------------------------------------------------------------------------------------------
// CustomMarkerView -
//-------------------------------------------------------------------------------------------------
class CustomMarkerView extends MarkerView {
    private final TextView tvContent;
    private final TextView tvDate;
    private final TextView tvTime;
    private ArrayList<Date> datesList;
    private String printedUnit;

    public CustomMarkerView(Context context, int layoutResource, ArrayList<Date> dates, String unit) {
        super(context, layoutResource);
        tvContent = findViewById(R.id.tvContent); // TextView in the marker layout
        tvDate = findViewById(R.id.tvDate);
        tvTime = findViewById(R.id.tvTime);
        datesList = dates;
        printedUnit = unit;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        tvContent.setText(String.format(Locale.getDefault(), "%.2f", e.getY()) + " " + printedUnit);
        if (datesList != null) {
            Date currentDate = datesList.get((int)e.getX());
            DateFormat shortDateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
            String shortPattern = ((SimpleDateFormat) shortDateFormat).toLocalizedPattern();
            DateFormat shorterDateFormat = new SimpleDateFormat(shortPattern, Locale.getDefault());
            // Format the Instant as a String
            String shorterString = shorterDateFormat.format(currentDate);
            tvDate.setText(shorterString);

            DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(tvDate.getContext());
            String localTime = timeFormat.format(currentDate);

            tvTime.setText(localTime);
        }

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}

//-------------------------------------------------------------------------------------------------
// MainActivity - MainActivity-class
//-------------------------------------------------------------------------------------------------
public class MainActivity extends AppCompatActivity {
    private DatabaseReference database;
    private final String deviceUuid = "7bfbe8a2-c91f-11ef-ab2e-b827ebd88bf2";
    private final String macAddress = "CD:19:C7:AF:16:2B";

    ArrayList<SensorInfo> sensorData;
    ArrayList<Date> dates;

    private LineChart tempLineChart;
    private XAxis tempXAxis;
    private LineChart humLineChart;
    private XAxis humXAxis;
    private LineChart pressLineChart;
    private XAxis pressXAxis;
    DateFormat dateFormat;
    DateFormat timeFormat;

    private boolean isSyncing = false;

    private Date convertUtcToDate(String utcTimestamp) {
        try {
            // Define the UTC time format
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            // Parse the input UTC timestamp and return it
            return utcFormat.parse(utcTimestamp);
        } catch (Exception e) {
            e.printStackTrace();
            return new Date();
        }
    }

    private void syncMarkers(LineChart sourceChart, LineChart... targetCharts) {
        sourceChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                if (isSyncing) return; // Avoid recursive calls

                isSyncing = true; // Set the flag
                try {
                    for (LineChart targetChart : targetCharts) {
                        Highlight targetHighlight = new Highlight(
                                e.getX(), e.getY(), h.getDataSetIndex());
                        targetChart.highlightValue(targetHighlight, true);
                    }
                } finally {
                    isSyncing = false; // Reset the flag
                }
            }

            @Override
            public void onNothingSelected() {
                if (isSyncing) return;

                isSyncing = true;
                try {
                    for (LineChart targetChart : targetCharts) {
                        targetChart.highlightValue(null);
                    }
                } finally {
                    isSyncing = false;
                }
            }
        });
    }


    private void syncCharts(Chart<?> sourceChart, Chart<?>... targetCharts) {

        sourceChart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

            @Override
            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture) {}

            @Override
            public void onChartLongPressed(MotionEvent me) {}

            @Override
            public void onChartDoubleTapped(MotionEvent me) {}

            @Override
            public void onChartSingleTapped(MotionEvent me) {}

            @Override
            public void onChartFling(MotionEvent me1, MotionEvent me2, float velocityX, float velocityY) {}

            @Override
            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
                for (Chart<?> targetChart : targetCharts) {
                    targetChart.getViewPortHandler().refresh(
                            sourceChart.getViewPortHandler().getMatrixTouch(),
                            targetChart,
                            true
                    );
                }
            }

            @Override
            public void onChartTranslate(MotionEvent me, float dX, float dY) {
                for (Chart<?> targetChart : targetCharts) {
                    targetChart.getViewPortHandler().refresh(
                            sourceChart.getViewPortHandler().getMatrixTouch(),
                            targetChart,
                            true
                    );
                }
            }
        });
    }

    private void setUniformYAxisWidth() {
        float maxLabelWidth = 0;

        // Measure the widest label width across all charts
        String widestLabel = "9,999"; // Give some value for the pressure...
        Paint paint = new Paint();
        paint.setTextSize(tempLineChart.getAxisLeft().getTextSize()); // Use the same text size
        maxLabelWidth = paint.measureText(widestLabel);

        // Set the width of the left axis for all charts
        YAxis tempYAxis = tempLineChart.getAxisLeft();
        YAxis humYAxis = humLineChart.getAxisLeft();
        YAxis pressYAxis = pressLineChart.getAxisLeft();

        tempYAxis.setMinWidth((int) maxLabelWidth);
        humYAxis.setMinWidth((int) maxLabelWidth);
        pressYAxis.setMinWidth((int) maxLabelWidth);
    }

    //---------------------------------------------------------------------------------------------
    // initCharts -
    //---------------------------------------------------------------------------------------------
    private void initCharts() {
        tempLineChart = findViewById(R.id.TemplineChart);
        humLineChart = findViewById(R.id.HumlineChart);
        pressLineChart = findViewById(R.id.PresslineChart);


        tempLineChart.getLegend().setEnabled(false);
        tempLineChart.getDescription().setEnabled(false);
        tempLineChart.getAxisRight().setEnabled(false);

        humLineChart.getLegend().setEnabled(false);
        humLineChart.getDescription().setEnabled(false);
        humLineChart.getAxisRight().setEnabled(false);

        pressLineChart.getLegend().setEnabled(false);
        pressLineChart.getDescription().setEnabled(false);
        pressLineChart.getAxisRight().setEnabled(false);

        syncCharts(tempLineChart, humLineChart, pressLineChart);
        syncCharts(humLineChart, tempLineChart, pressLineChart);
        syncCharts(pressLineChart, tempLineChart, humLineChart);

        tempXAxis = tempLineChart.getXAxis();
        tempXAxis.setAvoidFirstLastClipping(true);
        tempXAxis.setGranularity(1f);
        tempXAxis.setGranularityEnabled(true);
        tempXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        humXAxis = tempLineChart.getXAxis();
        humXAxis.setAvoidFirstLastClipping(true);
        humXAxis.setGranularity(1f);
        humXAxis.setGranularityEnabled(true);
        humXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        pressXAxis = tempLineChart.getXAxis();
        pressXAxis.setAvoidFirstLastClipping(true);
        pressXAxis.setGranularity(1f);
        pressXAxis.setGranularityEnabled(true);
        pressXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        setUniformYAxisWidth();

        CustomMarkerView markerView;

        markerView = new CustomMarkerView(this, R.layout.marker_view, dates, "°C");
        //markerView.setChartView(tempLineChart);
        markerView.setChartView(tempLineChart);
        tempLineChart.setMarker(markerView);

        markerView = new CustomMarkerView(this, R.layout.marker_view, dates, "%");
        markerView.setChartView(humLineChart);
        humLineChart.setMarker(markerView);

        markerView = new CustomMarkerView(this, R.layout.marker_view, dates, "hPa");
        markerView.setChartView(pressLineChart);
        pressLineChart.setMarker(markerView);

        syncMarkers(tempLineChart, humLineChart, pressLineChart);
        syncMarkers(humLineChart, tempLineChart, pressLineChart);
        syncMarkers(pressLineChart, tempLineChart, humLineChart);

        ValueFormatter dateFormatter = new ValueFormatter() {
            private Date previousDate = null; // Track the previous instant

            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < dates.size()) {
                    Date currentDate = dates.get(index);

                    // Get the formatted date and time in the phone's local format
                    String formattedTime = android.text.format.DateFormat.getTimeFormat(MainActivity.this).format(currentDate);
                    DateFormat shortDateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
                    String shortPattern = ((SimpleDateFormat) shortDateFormat).toLocalizedPattern();
                    String shorterPattern = shortPattern.replaceAll("[/\\- ]*[yY]+[^a-zA-Z]*", "");
                    DateFormat shorterDateFormat = new SimpleDateFormat(shorterPattern, Locale.getDefault());


                    // Check if the day has changed
                    if (previousDate != null && !isSameDay(previousDate, currentDate)) {
                        // Show date without year (only day and month)
                        previousDate = currentDate;
                        return shorterDateFormat.format(currentDate);
                    } else {
                        // Show only time
                        previousDate = currentDate;
                        return formattedTime;
                    }
                } else {
                    return "";
                }
            }

            // Helper method to check if two Instants are on the same day
            private boolean isSameDay(Date date1, Date date2) {
                Calendar cal1 = Calendar.getInstance();
                Calendar cal2 = Calendar.getInstance();
                cal1.setTime(date1);
                cal2.setTime(date2);
                return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
            }
        };

        tempXAxis.setValueFormatter(dateFormatter);
        humXAxis.setValueFormatter(dateFormatter);
        pressXAxis.setValueFormatter(dateFormatter);
    }

    //---------------------------------------------------------------------------------------------
    // drawCharts -
    //---------------------------------------------------------------------------------------------
    private void drawCharts() {
        ArrayList<Entry> tempLineEntries = new ArrayList<>();
        ArrayList<Entry> humLineEntries = new ArrayList<>();
        ArrayList<Entry> pressLineEntries = new ArrayList<>();

        float i = 0;
        for (SensorInfo sensor : sensorData) {
            tempLineEntries.add(new Entry(i, sensor.temperature.floatValue()));
            humLineEntries.add(new Entry(i, sensor.humidity.floatValue()));
            pressLineEntries.add(new Entry(i, sensor.pressure.floatValue()));
            i++;
        }

        LineDataSet tempLineDataSet = new LineDataSet(tempLineEntries, "Temperature");
        tempLineDataSet.setColor(Color.RED);
        tempLineDataSet.setDrawCircles(false);
        tempLineDataSet.setDrawValues(false);
        tempLineDataSet.setDrawFilled(true);
        tempLineDataSet.setFillColor(Color.argb(80, 255, 0, 0));
        LineData tempLineData = new LineData(tempLineDataSet);

        LineDataSet humLineDataSet = new LineDataSet(humLineEntries, "Humidity");
        humLineDataSet.setColor(Color.BLUE);
        humLineDataSet.setDrawCircles(false);
        humLineDataSet.setDrawValues(false);
        humLineDataSet.setDrawFilled(true);
        humLineDataSet.setFillColor(Color.argb(80, 0, 0, 255));
        LineData humLineData = new LineData(humLineDataSet);

        LineDataSet pressLineDataSet = new LineDataSet(pressLineEntries, "Pressure");
        pressLineDataSet.setColor(Color.rgb(0, 100, 0));
        pressLineDataSet.setDrawCircles(false);
        pressLineDataSet.setDrawValues(false);
        pressLineDataSet.setDrawFilled(true);
        pressLineDataSet.setFillColor(Color.argb(80, 0, 100, 0));
        LineData pressLineData = new LineData(pressLineDataSet);

        tempLineChart.setData(tempLineData);
        humLineChart.setData(humLineData);
        pressLineChart.setData(pressLineData);

        tempLineChart.invalidate();
        humLineChart.invalidate();
        pressLineChart.invalidate();
    }

    //---------------------------------------------------------------------------------------------
    // onCreate - Application starts here...
    //---------------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseAuth auth;

        dateFormat = android.text.format.DateFormat.getDateFormat(this);
        timeFormat = android.text.format.DateFormat.getTimeFormat(this);

        sensorData = new ArrayList<>();
        dates = new ArrayList<>();

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize the charts
        initCharts();

        // Firebase Authentication ----------------------------------------------------------------
        auth = FirebaseAuth.getInstance();
        String email = "tomi.isojarvi@isolinna.com";
        String pass = "1234567890";

        auth.signInWithEmailAndPassword(email, pass)
                // If authentication is successful...
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        Toast.makeText(MainActivity.this, "Login Successful",
                                Toast.LENGTH_SHORT).show();

                        // Get the current user
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

                        // Has current user be found?
                        if (currentUser != null) {
                            // Yes...

                            // Get the UserUID
                            String userUid = currentUser.getUid();

                            // Firebase Realtime Database Related ---------------------------------
                            // Initialize database reference.
                            database = FirebaseDatabase.getInstance().getReference("users")
                                    .child(userUid).child("devices").child(deviceUuid)
                                    .child(macAddress);

                            // Create AddValueEventListener
                            database.addValueEventListener(new ValueEventListener() {
                                @SuppressLint("NotifyDataSetChanged")

                                // If data has been changed...
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    sensorData.clear();
                                    dates.clear();

                                    // Update sensor data
                                    for (DataSnapshot sensorSnapshot : snapshot.getChildren()) {
                                        SensorInfo sensor =
                                                sensorSnapshot.getValue(SensorInfo.class);
                                        sensorData.add(sensor);
                                        dates.add(convertUtcToDate(sensor.utc_timestamp));
                                    }

                                    // Draw the charts
                                    drawCharts();
                                }

                                // If an error has been raised...
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Toast.makeText(MainActivity.this,
                                            "Error: " + error.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                            //---------------------------------------------------------------------

                        } else {
                            // No...
                            Toast.makeText(MainActivity.this,
                                    "No authenticated user found. Please log in again.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                // If authentication is a failure...
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Login Failed: " +
                                e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
        // ----------------------------------------------------------------------------------------
    }
}
