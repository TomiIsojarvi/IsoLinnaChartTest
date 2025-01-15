package com.example.mpandoirdcharttest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

//import com.example.mpandoirdcharttest.firebase.model.Information;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

class Information {
    public Double temperature, pressure, humidity, rssi, battery;
    public String utc_timestamp, mac;
}

public class MainActivity extends AppCompatActivity {
    private FirebaseAuth auth;
    DatabaseReference database;
    ArrayList<Information> list;
    private BarChart barChart;
    private LineChart tempLineChart;
    private XAxis tempXAxis;
    private LineChart humLineChart;
    private XAxis humXAxis;
    private LineChart pressLineChart;
    private XAxis pressXAxis;

    private String convertUtcToLocal(Context context, String utcTimestamp) {
        try {
            // Define the UTC time format
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

            // Parse the input UTC timestamp
            Date date = utcFormat.parse(utcTimestamp);

            // Use Android's default date and time patterns
            java.text.DateFormat dateFormat = DateFormat.getDateFormat(context);
            java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);

            // Format to current local date and time
            String formattedDate = dateFormat.format(date);
            String formattedTime = timeFormat.format(date);

            // Return the combined date and time
            return formattedDate + " " + formattedTime;
        } catch (Exception e) {
            e.printStackTrace();
            return utcTimestamp; // Fallback to UTC timestamp if exception
        }
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

        /*tempLineChart.invalidate();
        humLineChart.invalidate();
        pressLineChart.invalidate();*/
    }

    private void createCharts() {
        tempLineChart = findViewById(R.id.TemplineChart);
        humLineChart = findViewById(R.id.HumlineChart);
        pressLineChart = findViewById(R.id.PresslineChart);

        syncCharts(tempLineChart, humLineChart, pressLineChart);
        syncCharts(humLineChart, tempLineChart, pressLineChart);
        syncCharts(pressLineChart, tempLineChart, humLineChart);

        ArrayList<Entry> tempLineEntries = new ArrayList<>();
        ArrayList<Entry> humLineEntries = new ArrayList<>();
        ArrayList<Entry> pressLineEntries = new ArrayList<>();
        ArrayList<String> formattedDates = new ArrayList<>();

        ValueFormatter dateFormatter = new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < formattedDates.size()) {
                    return formattedDates.get(index);
                } else {
                    return "";
                }
            }
        };

        tempXAxis = tempLineChart.getXAxis();
        tempXAxis.setValueFormatter(dateFormatter);
        tempXAxis.setGranularity(1f);
        tempXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        humXAxis = humLineChart.getXAxis();
        humXAxis.setValueFormatter(dateFormatter);
        humXAxis.setGranularity(1f);
        humXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        pressXAxis = pressLineChart.getXAxis();
        pressXAxis.setValueFormatter(dateFormatter);
        pressXAxis.setGranularity(1f);
        pressXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        for (Information sensor : list) {
            formattedDates.add(convertUtcToLocal(this, sensor.utc_timestamp));
        }

        float i = 0;
        for (Information sensor : list) {
            tempLineEntries.add(new Entry(i, sensor.temperature.floatValue()));
            humLineEntries.add(new Entry(i, sensor.humidity.floatValue()));
            pressLineEntries.add(new Entry(i, sensor.pressure.floatValue()));
            i++;
        }

        LineDataSet tempLineDataSet = new LineDataSet(tempLineEntries, "Temperature");
        tempLineDataSet.setColor(Color.RED);
        tempLineDataSet.setDrawCircles(false);
        tempLineDataSet.setDrawFilled(true);
        tempLineDataSet.setFillColor(Color.argb(80, 255, 0, 0));
        LineData tempLineData = new LineData(tempLineDataSet);

        LineDataSet humLineDataSet = new LineDataSet(humLineEntries, "Humidity");
        humLineDataSet.setColor(Color.BLUE);
        humLineDataSet.setDrawCircles(false);
        humLineDataSet.setDrawFilled(true);
        humLineDataSet.setFillColor(Color.argb(80, 0, 0, 255));
        LineData humLineData = new LineData(humLineDataSet);

        LineDataSet pressLineDataSet = new LineDataSet(pressLineEntries, "Pressure");
        pressLineDataSet.setColor(Color.GREEN);
        pressLineDataSet.setDrawCircles(false);
        pressLineDataSet.setDrawFilled(true);
        pressLineDataSet.setFillColor(Color.argb(80, 0, 255, 0));
        LineData pressLineData = new LineData(pressLineDataSet);

        tempLineChart.getLegend().setEnabled(false);
        tempLineChart.getDescription().setEnabled(false);
        tempLineChart.setData(tempLineData);
        humLineChart.getLegend().setEnabled(false);
        humLineChart.getDescription().setEnabled(false);
        humLineChart.setData(humLineData);
        pressLineChart.getLegend().setEnabled(false);
        pressLineChart.getDescription().setEnabled(false);
        pressLineChart.setData(pressLineData);

        // Disable the right Y-axis
        tempLineChart.getAxisRight().setEnabled(false);
        humLineChart.getAxisRight().setEnabled(false);
        pressLineChart.getAxisRight().setEnabled(false);

        setUniformYAxisWidth();

        tempLineChart.invalidate();
        humLineChart.invalidate();
        pressLineChart.invalidate();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        list = new ArrayList<>();

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Firebase Authentication ----------------------------------------------------------------
        auth = FirebaseAuth.getInstance();
        String email = "tomi.isojarvi@isolinna.com";
        String pass = "1234567890";

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        Toast.makeText(MainActivity.this, "Login Successful",
                                Toast.LENGTH_SHORT).show();
                        try {
                            String userUid = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                            // Initialize database reference.
                            database = FirebaseDatabase.getInstance().getReference("users").child(userUid)
                                    .child("devices").child("7bfbe8a2-c91f-11ef-ab2e-b827ebd88bf2").child("CD:19:C7:AF:16:2B");

                            // Create AddValueEventListener
                            database.addValueEventListener(new ValueEventListener() {
                                @SuppressLint("NotifyDataSetChanged")
                                // onDataChange ---------------------
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    list.clear();

                                    for (DataSnapshot ruuvitagSnapshot : snapshot.getChildren()) {
                                        Information information = ruuvitagSnapshot.getValue(Information.class);
                                        //information.mac = ruuvitagSnapshot.getKey();
                                        list.add(information);
                                    }

                                    // Create the charts
                                    createCharts();
                                }
                                // onCancelled ----------------------
                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                }
                            });
                        } catch(NullPointerException e) {
                            Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }

                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MainActivity.this, "Login Failed: " +
                                e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
        // ----------------------------------------------------------------------------------------
    }
}
