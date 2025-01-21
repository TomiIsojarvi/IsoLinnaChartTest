package com.example.mpandoirdcharttest;

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

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import com.github.mikephil.charting.utils.MPPointF;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

//-------------------------------------------------------------------------------------------------
// SensorInfo - Used for storing sensor data
// (Tämä on jo projektissa mukana. Luokan ja tiedoston nimi pitää muuttaa projektissa Information:sta SensorInfo:ksi ja refactoroida)
//-------------------------------------------------------------------------------------------------
class SensorInfo {
    public Double temperature, pressure, humidity, rssi, battery;
    public String utc_timestamp, mac;
}

//-----------------------------------------------------------------------------------------------//
// CustomMarkerView -                                                                            //
//-----------------------------------------------------------------------------------------------//
class CustomMarkerView extends MarkerView {
    private final TextView tvContent;
    private final TextView tvDate;
    private final TextView tvTime;
    private ArrayList<Date> datesList;
    private String printedUnit;
    java.text.DateFormat dateFormatter;
    java.text.DateFormat timeFormatter;

    //---------------------------------------------------------------------------------------------
    // Constructors
    //---------------------------------------------------------------------------------------------
    public CustomMarkerView(Context context) {
        super(context, R.layout.marker_view);

        tvContent = findViewById(R.id.tvContent);
        tvDate = findViewById(R.id.tvDate);
        tvTime = findViewById(R.id.tvTime);
    }

    public CustomMarkerView(
            Context context, int layoutResource, ArrayList<Date> dates, String unit,
            java.text.DateFormat dateFormat, java.text.DateFormat timeFormat
    ) {
        super(context, layoutResource);

        tvContent = findViewById(R.id.tvContent);
        tvDate = findViewById(R.id.tvDate);
        tvTime = findViewById(R.id.tvTime);

        datesList = dates;
        printedUnit = unit;
        dateFormatter = dateFormat;
        timeFormatter = timeFormat;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        tvContent.setText(String.format(Locale.getDefault(), "%.2f", e.getY()) + " " + printedUnit);
        if (datesList != null && dateFormatter != null && timeFormatter != null) {
            Date currentDate = datesList.get((int)e.getX());

            tvDate.setText(dateFormatter.format(currentDate));
            tvTime.setText(timeFormatter.format(currentDate));
        }

        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }
}

//-----------------------------------------------------------------------------------------------//
// MainActivity - Main Activity                                                                  //
//-----------------------------------------------------------------------------------------------//
public class MainActivity extends AppCompatActivity {
    // Firebase Realtime Database related
    private DatabaseReference database;

    // Nämä arvot pitää tulla kutsuvalta Activitylta ja final pitää poistaa...
    private final String deviceUuid = "7bfbe8a2-c91f-11ef-ab2e-b827ebd88bf2";
    private final String macAddress = "CD:19:C7:AF:16:2B";
    private final String userUid = "Zu3AC6ctgue483lGhSwS49VXspt2";

    // Stored sensor info and dates
    private ArrayList<SensorInfo> sensorData;
    private ArrayList<Date> dates;

    // Date and time formatters
    private java.text.DateFormat dateFormatter;
    private java.text.DateFormat timeFormatter;

    // Charts
    private LineChart tempLineChart;
    private LineChart humLineChart;
    private LineChart pressLineChart;

    // Used for syncing markers
    private boolean isSyncing = false;

    //---------------------------------------------------------------------------------------------
    // convertUtcToDate - Converts UTC timestamp from String to Date-object
    //---------------------------------------------------------------------------------------------
    private Date convertUtcToDate(String utcTimestamp) {
        try {
            // Define the UTC time format
            SimpleDateFormat utcFormat =
                    new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            // Parse the input UTC timestamp and return it
            return utcFormat.parse(utcTimestamp);
        } catch (Exception e) {
            // Return current Date if an error...
            return new Date();
        }
    }

    //---------------------------------------------------------------------------------------------
    // syncCharts - Used for synchronizing the charts' gestures
    //---------------------------------------------------------------------------------------------
    private void syncCharts(Chart<?> sourceChart, Chart<?>... targetCharts) {
        sourceChart.setOnChartGestureListener(new OnChartGestureListener() {

            // Not used ---------------------------------------------------------------------------
            @Override
            public void onChartGestureStart(
                    MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture
            ) {}
            @Override
            public void onChartGestureEnd(
                    MotionEvent me, ChartTouchListener.ChartGesture lastPerformedGesture
            ) {}
            @Override
            public void onChartLongPressed(MotionEvent me) {}
            @Override
            public void onChartDoubleTapped(MotionEvent me) {}
            @Override
            public void onChartSingleTapped(MotionEvent me) {}
            @Override
            public void onChartFling(
                    MotionEvent me1, MotionEvent me2, float velocityX, float velocityY
            ) {}
            // ------------------------------------------------------------------------------------

            // Scaling ----------------------------------------------------------------------------
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
            // ------------------------------------------------------------------------------------

            // Translation ------------------------------------------------------------------------
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
            // ------------------------------------------------------------------------------------
        });
    }

    //---------------------------------------------------------------------------------------------
    // syncMarkers - Used for synchronizing the charts' markers
    //---------------------------------------------------------------------------------------------
    private void syncMarkers(LineChart sourceChart, LineChart... targetCharts) {
        sourceChart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {

            // Value selected ---------------------------------------------------------------------
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
            // ------------------------------------------------------------------------------------

            // Nothing selected -------------------------------------------------------------------
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
            // ------------------------------------------------------------------------------------
        });
    }

    //---------------------------------------------------------------------------------------------
    // setUniformYAxisWidth - Makes left Y-axes to be same width
    //---------------------------------------------------------------------------------------------
    private void setUniformYAxisWidth() {
        float maxLabelWidth;

        // Measure the widest label width across all charts
        String widestLabel = "X.XXX";   // Give some example for the widest label
        Paint paint = new Paint();
        paint.setTextSize(tempLineChart.getAxisLeft().getTextSize()); // Use the same text size
        maxLabelWidth = paint.measureText(widestLabel);

        // Set the width of the left axis for all charts
        tempLineChart.getAxisLeft().setMinWidth((int) maxLabelWidth);
        humLineChart.getAxisLeft().setMinWidth((int) maxLabelWidth);
        pressLineChart.getAxisLeft().setMinWidth((int) maxLabelWidth);
    }

    //---------------------------------------------------------------------------------------------
    // initAxes - Initialize the chart axes
    //---------------------------------------------------------------------------------------------
    private void initAxes() {
        // Temperature
        XAxis tempXAxis = tempLineChart.getXAxis();
        tempXAxis.setAvoidFirstLastClipping(true);
        tempXAxis.setGranularity(1f);
        tempXAxis.setGranularityEnabled(true);
        tempXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        // Humidity
        XAxis humXAxis = humLineChart.getXAxis();
        humXAxis.setAvoidFirstLastClipping(true);
        humXAxis.setGranularity(1f);
        humXAxis.setGranularityEnabled(true);
        humXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        // Pressure
        XAxis pressXAxis = pressLineChart.getXAxis();
        pressXAxis.setAvoidFirstLastClipping(true);
        pressXAxis.setGranularity(1f);
        pressXAxis.setGranularityEnabled(true);
        pressXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        // Make the Y-axis same width
        setUniformYAxisWidth();

        // Value Formatter for the dates ----------------------------------------------------------
        ValueFormatter dateFormatter = new ValueFormatter() {
            private Date previousDate = null; // Track the previous date

            // Get Formatted Value ----------------------------------------------------------------
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < dates.size()) {
                    Date currentDate = dates.get(index);

                    // Get formatted time
                    String formattedTime = timeFormatter.format(currentDate);

                    // Get formatted date in Day/Month
                    String dayMonthPattern = android.text.format.DateFormat
                            .getBestDateTimePattern(Locale.getDefault(), "Md");
                    SimpleDateFormat dayMonthFormat =
                            new SimpleDateFormat(dayMonthPattern, Locale.getDefault());
                    String formattedDate = dayMonthFormat.format(currentDate);

                    // Check if the day has changed
                    if (previousDate != null && !isSameDay(previousDate, currentDate)) {
                        // Show date without year (only day and month)
                        previousDate = currentDate;
                        return formattedDate;
                    } else {
                        // Show only time
                        previousDate = currentDate;
                        return formattedTime;
                    }
                } else {
                    return "";
                }
            }
            // ------------------------------------------------------------------------------------

            // Helper method to check if two Dates are on the same day ----------------------------
            private boolean isSameDay(Date date1, Date date2) {
                if (date1 == null || date2 == null) {
                    return false;
                }
                LocalDate localDate1 = date1.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate localDate2 = date2.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                return localDate1.isEqual(localDate2);
            }
        };
        // ----------------------------------------------------------------------------------------

        // Attach the value formatter to X-axes
        tempXAxis.setValueFormatter(dateFormatter);
        humXAxis.setValueFormatter(dateFormatter);
        pressXAxis.setValueFormatter(dateFormatter);
    }

    //---------------------------------------------------------------------------------------------
    // initCharts - Initialize the charts
    //---------------------------------------------------------------------------------------------
    private void initCharts() {
        tempLineChart = findViewById(R.id.TemplineChart);
        humLineChart = findViewById(R.id.HumlineChart);
        pressLineChart = findViewById(R.id.PresslineChart);

        // Temperature
        tempLineChart.getLegend().setEnabled(false);
        tempLineChart.getDescription().setEnabled(false);
        tempLineChart.getAxisRight().setEnabled(false);

        // Humidity
        humLineChart.getLegend().setEnabled(false);
        humLineChart.getDescription().setEnabled(false);
        humLineChart.getAxisRight().setEnabled(false);

        // Pressure
        pressLineChart.getLegend().setEnabled(false);
        pressLineChart.getDescription().setEnabled(false);
        pressLineChart.getAxisRight().setEnabled(false);

        // Synchronize the charts
        syncCharts(tempLineChart, humLineChart, pressLineChart);
        syncCharts(humLineChart, tempLineChart, pressLineChart);
        syncCharts(pressLineChart, tempLineChart, humLineChart);

        // Initialize the axes
        initAxes();

        // Set up the Markers ---------------------------------------------------------------------
        CustomMarkerView markerView;

        // Temperature
        markerView = new CustomMarkerView(
                this, R.layout.marker_view, dates, "°C", dateFormatter, timeFormatter
        );
        markerView.setChartView(tempLineChart);
        tempLineChart.setMarker(markerView);

        // Humidity
        markerView = new CustomMarkerView(
                this, R.layout.marker_view, dates, "%", dateFormatter, timeFormatter
        );
        markerView.setChartView(humLineChart);
        humLineChart.setMarker(markerView);

        // Pressure
        markerView = new CustomMarkerView(
                this, R.layout.marker_view, dates, "hPa", dateFormatter, timeFormatter
        );
        markerView.setChartView(pressLineChart);
        pressLineChart.setMarker(markerView);

        // Synchronize the markers
        syncMarkers(tempLineChart, humLineChart, pressLineChart);
        syncMarkers(humLineChart, tempLineChart, pressLineChart);
        syncMarkers(pressLineChart, tempLineChart, humLineChart);
        // ----------------------------------------------------------------------------------------
    }

    //---------------------------------------------------------------------------------------------
    // drawCharts - Draw the charts
    //---------------------------------------------------------------------------------------------
    private void drawCharts() {
        ArrayList<Entry> tempLineEntries = new ArrayList<>();
        ArrayList<Entry> humLineEntries = new ArrayList<>();
        ArrayList<Entry> pressLineEntries = new ArrayList<>();

        // Initialise the Data Entries
        float i = 0;
        for (SensorInfo sensor : sensorData) {
            tempLineEntries.add(new Entry(i, sensor.temperature.floatValue()));
            humLineEntries.add(new Entry(i, sensor.humidity.floatValue()));
            pressLineEntries.add(new Entry(i, sensor.pressure.floatValue()));
            i++;
        }

        // Initialise the Temperature Line Data
        LineDataSet tempLineDataSet = new LineDataSet(tempLineEntries, "Temperature");
        tempLineDataSet.setColor(Color.RED);
        tempLineDataSet.setDrawCircles(false);
        tempLineDataSet.setDrawValues(false);
        tempLineDataSet.setDrawFilled(true);
        tempLineDataSet.setFillColor(Color.argb(80, 255, 0, 0));
        LineData tempLineData = new LineData(tempLineDataSet);

        // Initialise the Humidity Line Data
        LineDataSet humLineDataSet = new LineDataSet(humLineEntries, "Humidity");
        humLineDataSet.setColor(Color.BLUE);
        humLineDataSet.setDrawCircles(false);
        humLineDataSet.setDrawValues(false);
        humLineDataSet.setDrawFilled(true);
        humLineDataSet.setFillColor(Color.argb(80, 0, 0, 255));
        LineData humLineData = new LineData(humLineDataSet);

        // Initialise the Pressure Line Data
        LineDataSet pressLineDataSet = new LineDataSet(pressLineEntries, "Pressure");
        pressLineDataSet.setColor(Color.rgb(0, 100, 0));
        pressLineDataSet.setDrawCircles(false);
        pressLineDataSet.setDrawValues(false);
        pressLineDataSet.setDrawFilled(true);
        pressLineDataSet.setFillColor(Color.argb(80, 0, 100, 0));
        LineData pressLineData = new LineData(pressLineDataSet);

        // Attach line data to charts
        tempLineChart.setData(tempLineData);
        humLineChart.setData(humLineData);
        pressLineChart.setData(pressLineData);

        // Invalidate
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

        // Initialize date and time formatters to use device's date formats
        timeFormatter = android.text.format.DateFormat.getTimeFormat(this);
        dateFormatter = android.text.format.DateFormat.getDateFormat(this);

        FirebaseAuth auth;
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
                .addOnSuccessListener(authResult -> {
                    Toast.makeText(MainActivity.this, "Login Successful", Toast.LENGTH_SHORT).show();

                    // Get the current user
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

                    // Is current user to be found?
                    if (currentUser != null) {
                        // Yes...

                        // Get the UserUID
                        //String userUid = currentUser.getUid();

                        // Firebase Realtime Database Related ---------------------------------
                        // Initialize database reference
                        database = FirebaseDatabase.getInstance().getReference("users")
                                .child(userUid).child("devices").child(deviceUuid)
                                .child(macAddress);

                        // Create AddValueEventListener
                        database.addValueEventListener(new ValueEventListener() {
                            @SuppressLint("NotifyDataSetChanged")
                            // If sensor data has been changed...
                            @Override
                            public void onDataChange(@NonNull DataSnapshot snapshot) {
                                sensorData.clear();
                                dates.clear();

                                // Update sensor data
                                for (DataSnapshot sensorSnapshot : snapshot.getChildren()) {
                                    SensorInfo sensor = sensorSnapshot.getValue(SensorInfo.class);

                                    if (sensor != null) {
                                        sensorData.add(sensor);
                                        dates.add(convertUtcToDate(sensor.utc_timestamp));
                                    }
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
                })
                // If authentication fails...
                .addOnFailureListener(e ->
                        Toast.makeText(MainActivity.this, "Login Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );

        // ----------------------------------------------------------------------------------------
    }
}
