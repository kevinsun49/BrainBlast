package com.bose.wearable.sample;

//
//  GestureDataFragment.java
//  BoseWearable
//
//  Created by Tambet Ingo on 11/02/2018.
//  Copyright © 2018 Bose Corporation. All rights reserved.
//

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.bose.wearable.sample.viewmodels.GestureEvent;
import com.bose.wearable.sample.viewmodels.SessionViewModel;
import com.bose.wearable.services.wearablesensor.GestureConfiguration;
import com.bose.wearable.services.wearablesensor.GestureType;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.RecyclerView;

public class GestureDataFragment extends Fragment {
    @SuppressWarnings("PMD.SingularField") // Need to keep a reference to it so it does not get GC'd
    private SessionViewModel mViewModel;
    private RecyclerView mRecyclerView;
    private GestureDataAdapter mAdapter;
    @NonNull
    private GestureConfiguration mGestureConf = GestureConfiguration.EMPTY;
    private RequestQueue queue;
    private String url = "http://10.73.94.150:3000/make_coffee";
    private FusedLocationProviderClient locationProviderClient;
    private Location baseCoffeeLocation;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private float minDistance = 1000;

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        locationProviderClient = LocationServices.getFusedLocationProviderClient(getActivity().getApplicationContext());
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        baseCoffeeLocation = locationProviderClient.getLastLocation().getResult();
        baseCoffeeLocation.setLatitude(0);
        baseCoffeeLocation.setLongitude(0);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_gesture_data, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRecyclerView = view.findViewById(R.id.list);
        mAdapter = new GestureDataAdapter();
        mRecyclerView.setAdapter(mAdapter);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        queue = Volley.newRequestQueue(getActivity().getApplicationContext());
        mViewModel = ViewModelProviders.of(requireActivity()).get(SessionViewModel.class);

        mViewModel.wearableGestureConfiguration()
                .observe(this, this::onGesturesRead);

        mViewModel.gestureEvents()
                .observe(this, this::onGesture);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                if (locationResult.getLastLocation().distanceTo(baseCoffeeLocation) < minDistance) {
                    Vibrator v = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                    // Vibrate for 500 milliseconds
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        //deprecated in API 26
                        v.vibrate(500);
                    }
                }
            }
        };

        locationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);

        mAdapter.replace(mViewModel.gestures());
    }

    private void onGesturesRead(GestureConfiguration gestureConfiguration) {
        mGestureConf = gestureConfiguration;

        //Toast.makeText(getActivity().getApplicationContext(), "on gesutres read " + mGestureConf.allGestures().toString(), Toast.LENGTH_LONG).show();

        enableAll();
    }

    private void onGesture(Event<GestureEvent> event) {
        final GestureEvent gestureEvent = event.get();
        if (gestureEvent != null) {
            switch (gestureEvent.gestureData().type()) {
                case HEAD_NOD:
                case DOUBLE_TAP:
                case AFFIRMATIVE:
                    //say ok and send post
                    StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                            new Response.Listener<String>() {
                                @Override
                                public void onResponse(String response) {
                                    Toast.makeText(getActivity().getApplicationContext(), "Affirmative!", Toast.LENGTH_LONG).show();
                                }
                            }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Toast.makeText(getActivity().getApplicationContext(), "Error", Toast.LENGTH_LONG).show();
                        }
                    });
                    queue.add(stringRequest);
                    break;
                case NEGATIVE:
                    Toast.makeText(getActivity().getApplicationContext(), "Negative", Toast.LENGTH_LONG).show();
                    break;
            }
            mAdapter.addGestureEvent(gestureEvent);
            mRecyclerView.scrollToPosition(mAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.gesture_data_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear:
                mViewModel.clearGestures();
                mAdapter.clear();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void set(@NonNull final GestureType gestureType, final boolean enabled) {
        doChange(mGestureConf.gestureEnabled(gestureType, enabled));
        Toast.makeText(getActivity().getApplicationContext(), "set bitches", Toast.LENGTH_LONG).show();

    }

    private void enableAll() {
        doChange(mGestureConf.enableAll());
    }

    private void disableAll() {
        doChange(mGestureConf.disableAll());
    }

    private void doChange(@NonNull final GestureConfiguration newConf) {
        //Toast.makeText(getActivity().getApplicationContext(), "enabled " + newConf.enabledGestures().toString(), Toast.LENGTH_LONG).show();

        if (!newConf.equals(mGestureConf)) {
            mViewModel.changeGestureConfiguration(newConf);
        }
        //Toast.makeText(getActivity().getApplicationContext(), "changed? " + newConf.enabledGestures().toString(), Toast.LENGTH_LONG).show();

    }


}
