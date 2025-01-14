package com.example.mpandoirdcharttest;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

//import com.example.mpandoirdcharttest.firebase.model.Information;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
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


    private void createCharts() {
        // Initialize the BarChart after setContentView
        tempLineChart = findViewById(R.id.TemplineChart);
        humLineChart = findViewById(R.id.HumlineChart);
        pressLineChart = findViewById(R.id.PresslineChart);

        ArrayList<Entry> tempLineEntries = new ArrayList<>();
        ArrayList<Entry> humLineEntries = new ArrayList<>();
        ArrayList<Entry> pressLineEntries = new ArrayList<>();

        ArrayList<String> formattedDates = new ArrayList<>();
        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat localFormat = new SimpleDateFormat("dd.MM.yyyy HH.mm.ss", Locale.getDefault());
        localFormat.setTimeZone(TimeZone.getDefault());

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
        tempXAxis.setGranularity(1f); // Ensure labels correspond to entries
        tempXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        humXAxis = humLineChart.getXAxis();
        humXAxis.setValueFormatter(dateFormatter);
        humXAxis.setGranularity(1f); // Ensure labels correspond to entries
        humXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        pressXAxis = pressLineChart.getXAxis();
        pressXAxis.setValueFormatter(dateFormatter);
        pressXAxis.setGranularity(1f); // Ensure labels correspond to entries
        pressXAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        for (Information sensor : list) {
            try {
                Date date = utcFormat.parse(sensor.utc_timestamp);
                formattedDates.add(localFormat.format(date));
            } catch (Exception e) {
                e.printStackTrace();
            }
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
        //tempLineDataSet.setCircleColor(Color.RED);
        tempLineDataSet.setDrawCircles(false);
        LineData tempLineData = new LineData(tempLineDataSet);
        LineDataSet humLineDataSet = new LineDataSet(humLineEntries, "Humidity");
        humLineDataSet.setColor(Color.BLUE);
        //humLineDataSet.setCircleColor(Color.BLUE);
        humLineDataSet.setDrawCircles(false);
        LineData humLineData = new LineData(humLineDataSet);
        LineDataSet pressLineDataSet = new LineDataSet(pressLineEntries, "Pressure");
        pressLineDataSet.setColor(Color.GREEN);
        //pressLineDataSet.setCircleColor(Color.BLUE);
        pressLineDataSet.setDrawCircles(false);
        LineData pressLineData = new LineData(pressLineDataSet);

        tempLineChart.setData(tempLineData);
        humLineChart.setData(humLineData);
        pressLineChart.setData(pressLineData);
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
                                    .child("devices").child("7bfbe8a2-c91f-11ef-ab2e-b827ebd88bf2").child("CB:73:41:B7:0A:50");

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
