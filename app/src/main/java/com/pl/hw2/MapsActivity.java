package com.pl.hw2;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.animation.Animator;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLoadedCallback, GoogleMap.OnMarkerClickListener, GoogleMap.OnMapLongClickListener {
    private GoogleMap mMap;
    private static final int MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 101;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback locationCallback;
    private ImageButton start = null;
    private ImageButton stop = null;
    private TextView meter = null;
    FloatingActionButton fab = null;
    private SensorManager sensorManager = null;
    private boolean updatable = false;
    Marker gpsMarker = null;
    List<Marker> markerList;
    List<LatLng> latLngList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        start = findViewById(R.id.start_button);
        stop = findViewById(R.id.stop_button);
        meter = findViewById(R.id.meter);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        markerList = new ArrayList<>();
        latLngList = new ArrayList<>();

        start.setVisibility(View.INVISIBLE);
        stop.setVisibility(View.INVISIBLE);
        meter.setVisibility(View.INVISIBLE);

        fab = findViewById(R.id.floatingActionButton);
        fab.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                for (Marker i: markerList) {
                    i.remove();
                }
                markerList.clear();
                latLngList.clear();
                mMap.moveCamera(CameraUpdateFactory.zoomTo(0f));

                if(start != null && stop != null && meter != null){
                    updatable = false;
                    hide(stop);
                    hide(start);
                    hide(meter);
                }
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatable = true;
            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updatable = false;
                hide(stop);
                hide(start);
                hide(meter);
                mMap.moveCamera(CameraUpdateFactory.zoomTo(0f));
            }
        });

        sensorManager.registerListener(new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (meter != null && updatable) {
                    meter.setText(String.format("Acceleration:\n x: %.5f, y: %.5f", event.values[0], event.values[1]));
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        }, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void onDestroy() {
        saveData(latLngList);
        super.onDestroy();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLoadedCallback(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setOnMapLongClickListener(this);

        restoreData();
        Log.v("hw2", "Data restored");
    }

    @Override
    public void onMapLoaded() {
        Log.i(MapsActivity.class.getSimpleName(), "MapLoaded");
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSION_REQUEST_ACCESS_FINE_LOCATION);
            return;
        }
        Task<Location> lastLocation = fusedLocationClient.getLastLocation();

        lastLocation.addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null && mMap != null) {
                    mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(location.getLatitude(), location.getLongitude()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                            .title(String.valueOf(R.string.last_known_loc_msg)));
                }
            }
        });

        createLocationRequest();
        createLocationCallback();
        startLocationUpdates();
    }

    private void createLocationRequest(){
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(10000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void createLocationCallback(){
        locationCallback = new LocationCallback(){
            @Override
            public void onLocationResult(LocationResult locationResult){
                if(locationResult != null){
                    if(gpsMarker != null) gpsMarker.remove();
                    Location location = locationResult.getLastLocation();
                    gpsMarker = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(location.getLatitude(), location.getLongitude()))
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                            .alpha(0.8f)
                            .title("Current Location"));
                }
            }

        };
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates(){
        fusedLocationClient.requestLocationUpdates(mLocationRequest, locationCallback, null);
    }

    @Override
    protected void onPause(){
        super.onPause();
        stopLocationUpdates();
    }

    private void stopLocationUpdates(){
        if(locationCallback != null) fusedLocationClient.removeLocationUpdates(locationCallback);
    }


    @Override
    public void onMapLongClick(LatLng latLng) {
        LatLng pos = new LatLng(latLng.latitude, latLng.longitude);
        MarkerOptions m = new MarkerOptions()
                .position(pos)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                .alpha(0.8f)
                .title(String.format("Position: (%.2f, %.2f)", latLng.latitude, latLng.longitude));
        Marker marker = mMap.addMarker(m);
        markerList.add(marker);
        latLngList.add(pos);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        CameraPosition cameraPosition = mMap.getCameraPosition();
        if(cameraPosition.zoom < 14f) mMap.moveCamera(CameraUpdateFactory.zoomTo(14f));

        if(start != null && stop != null && meter != null){
            show(stop);
            show(start);
            show(meter);
            meter.setText("Push start button to activate");
        }
        return false;
    }

    public void zoomInClick(View view) {
        mMap.moveCamera(CameraUpdateFactory.zoomIn());
    }

    public void zoomOutClick(View view) {
        mMap.moveCamera(CameraUpdateFactory.zoomOut());
    }

    protected void hide(final View v){
        v.animate().translationY(0).alpha(0f).setDuration(1000).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                v.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    protected void show(final View v){
        v.animate().translationY(0).alpha(1f).setDuration(1000).setListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

            }

            @Override
            public void onAnimationEnd(Animator animation) {
                v.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {

            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
    }

    private void saveData(List<LatLng> markers){
        Gson gson = new Gson();
        String to_save = gson.toJson(markers);
        FileOutputStream fileOutputStream;

        try {
            fileOutputStream = openFileOutput("markers.json", MODE_PRIVATE);
            FileWriter writer = new FileWriter(fileOutputStream.getFD());
            writer.write(to_save);
            writer.close();

        } catch (FileNotFoundException e){
            Log.e("hw2", "Error occurred: "+ e.getMessage());
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void restoreData(){
        FileInputStream fileInputStream;
        Gson gson = new Gson();
        int DEFAULT_BUFFER_SIZE = 10000;
        String json;;

        try{
            fileInputStream = openFileInput("markers.json");
            FileReader reader = new FileReader((fileInputStream.getFD()));
            char[] buf = new char[DEFAULT_BUFFER_SIZE];
            int n;

            StringBuilder builder = new StringBuilder();
            while ((n = reader.read(buf)) >= 0){
                String tmp = String.valueOf(buf);
                String substring = (n<DEFAULT_BUFFER_SIZE) ? tmp.substring(0, n) : tmp;
                builder.append(substring);
            }

            reader.close();
            json = builder.toString();
            Type collectionType = new TypeToken<List<LatLng>>(){}.getType();
            List<LatLng> o = gson.fromJson(json, collectionType);

            if(o != null){
                markerList.clear();
                latLngList.clear();

                for(LatLng m: o) {
                    Log.v("hw2", m.latitude + ", " + m.longitude);
                    LatLng latLng = new LatLng(m.latitude, m.longitude);
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(latLng)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                            .alpha(0.8f)
                            .title("Restored from memory");

                    try {
                        markerList.add(mMap.addMarker(markerOptions));
                        latLngList.add(latLng);
                    } catch (Exception e){
                        Log.e("hw2", e.getMessage());
                    }
                }

            } else {
                Log.e("hw2", "File is empty");
            }
        } catch (IOException e){
            Log.e("hw2", e.getMessage());
        }
    }
}
