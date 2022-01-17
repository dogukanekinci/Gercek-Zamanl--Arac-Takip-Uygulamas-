package com.example.gercekzamanliaractakipbinici.ui.home;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.gercekzamanliaractakipbinici.Callback.IFirebaseDriverInfolistener;
import com.example.gercekzamanliaractakipbinici.Callback.IFirebaseFailedlistener;
import com.example.gercekzamanliaractakipbinici.Common.Common;
import com.example.gercekzamanliaractakipbinici.Model.DriverGeoModel;
import com.example.gercekzamanliaractakipbinici.Model.DriverInfoModel;
import com.example.gercekzamanliaractakipbinici.Model.GeoQueryModel;
import com.example.gercekzamanliaractakipbinici.R;
import com.example.gercekzamanliaractakipbinici.databinding.FragmentHomeBinding;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class HomeFragment extends Fragment implements OnMapReadyCallback, IFirebaseFailedlistener, IFirebaseDriverInfolistener {

    private HomeViewModel homeViewModel;
    private GoogleMap mMap;
    private SupportMapFragment mapFragment;

    //Location
    private FusedLocationProviderClient fusedLocationProviderclient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    //Load Driver
    private double distance = 1.0; // default in km
    private static final double LIMIT_RANGE = 10.0; // km
    private Location previousLocation, currentLocation; //Use to calculate distance

    private boolean firstTime = true;


    //Listener

    IFirebaseDriverInfolistener iFirebaseDriverInfolistener;
    IFirebaseFailedlistener iFirebaseFailedListener;
    private String cityName;

    @Override
    public void onDestroy() {
        fusedLocationProviderclient.removeLocationUpdates(locationCallback);

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        View root = inflater.inflate(R.layout.fragment_home, container, false);

        init();
        mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        return root;
    }

    private void init() {
        iFirebaseFailedListener = this;
        iFirebaseDriverInfolistener = this;

        locationRequest = new LocationRequest();
        locationRequest.setSmallestDisplacement(10f);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(3000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);

                LatLng newPosition = new LatLng(locationResult.getLastLocation().getLatitude(),
                        locationResult.getLastLocation().getLongitude());
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPosition, 18f));

                //kullanıcı lokasyon değiştirirse, tekrar hesaplanıp yüklenecek

                if (firstTime) {
                    previousLocation = currentLocation = locationResult.getLastLocation();
                    firstTime = false;
                } else {
                    previousLocation = currentLocation;
                    currentLocation = locationResult.getLastLocation();

                }
                if (previousLocation.distanceTo(currentLocation) / 1000 <= LIMIT_RANGE)

                    loadAvailableDrivers();
                else {

                }
            }
        };

        fusedLocationProviderclient = LocationServices.getFusedLocationProviderClient(getContext());
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        fusedLocationProviderclient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        //off init
        loadAvailableDrivers();
    }

    private void loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                &&
                ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(getView(), getString(R.string.permission_require), Snackbar.LENGTH_SHORT).show();

            return;
        }
        fusedLocationProviderclient.getLastLocation()
                .addOnFailureListener(e -> Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show())
                .addOnSuccessListener(location -> {
                    //Load all driver incity
                    Geocoder geocoder = new Geocoder (getContext (), Locale.getDefault());
                    List<Address> addressList;
                    try {
                        addressList = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                        cityName = addressList.get(0).getAdminArea();
                        //Query
                        DatabaseReference driver_location_ref = FirebaseDatabase.getInstance()
                                    .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                                .child(cityName);

                        GeoFire gf  = new GeoFire(driver_location_ref);
                                GeoQuery geoQuery = gf.queryAtLocation(new GeoLocation(location.getLatitude(),
                                location.getLongitude()), distance);
                        geoQuery.removeAllListeners();

                        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                            @Override
                            public void onKeyEntered(String key, GeoLocation location) {
                                Common.driversFound.add(new DriverGeoModel(key,location));

                            }

                            @Override
                            public void onKeyExited(String key) {

                            }

                            @Override
                            public void onKeyMoved(String key, GeoLocation location) {

                            }

                            @Override
                            public void onGeoQueryReady() {
                                if(distance <= LIMIT_RANGE) {
                                    distance++;
                                    loadAvailableDrivers(); // Yeni mesafede aramaya devam et
                                }
                                else
                                    {
                                        distance = 1.0; // Reset it
                                        addDriverMarker();
                                    }

                            }

                            @Override
                            public void onGeoQueryError(DatabaseError error) {
                                Snackbar.make(getView(), error.getMessage(),Snackbar.LENGTH_SHORT).show();


                            }
                        });

                        driver_location_ref.addChildEventListener(new ChildEventListener() {
                            @Override
                            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {
                                GeoQueryModel geoQueryModel = dataSnapshot.getValue(GeoQueryModel.class);
                                GeoLocation geoLocation = new GeoLocation(geoQueryModel.getL().get(0),
                                        geoQueryModel.getL().get(1));
                                DriverGeoModel driverGeoModel = new DriverGeoModel(dataSnapshot.getKey(),
                                        geoLocation);
                                Location newDriverLocation = new Location("");
                                newDriverLocation.setLatitude(geoLocation.latitude);
                                newDriverLocation.setLongitude(geoLocation.longitude);
                                float newDistance = location.distanceTo(newDriverLocation) / 1000; //km
                                if (newDistance<-LIMIT_RANGE)
                                    findDriverBykey(driverGeoModel);//sürücü  range aralığında ise mapte gözükecek

                            }

                            @Override
                            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {

                            }

                            @Override
                            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                            }

                            @Override
                            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String previousChildName) {

                            }

                            @Override
                            public void onCancelled(@NonNull DatabaseError databaseError) {

                            }
                        });


                    } catch (IOException e) {
                        e.printStackTrace();
                        Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show();
                    }

                });
    }

    private void addDriverMarker() {
        if(Common.driversFound.size() > 0)
        {
            Observable.fromIterable(Common.driversFound)
                    .subscribeOn (Schedulers.newThread())
                    .observeOn (AndroidSchedulers. mainThread())
                    . subscribe(driverGeoModel -> {
                        //on next
                        findDriverBykey(driverGeoModel);
                    },throwable -> {
                        Snackbar.make(getView(), throwable.getMessage(), Snackbar.LENGTH_SHORT).show();
                    },()->{});

        }

        else
            {
                    Snackbar.make(getView(),getString(R.string.drivers_not_found), Snackbar.LENGTH_SHORT). show();
            }
    }

    private void findDriverBykey(DriverGeoModel driverGeoModel) {

        FirebaseDatabase.getInstance()
       .getReference(Common.DRIVER_INFO_REFERENCE)
                .child(driverGeoModel.getKey())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange (@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.hasChildren())
                        {
                            driverGeoModel.setDriverInfoModel (dataSnapshot.getValue (DriverInfoModel.class));
                            iFirebaseDriverInfolistener.onDriverInfoLoadSuccess (driverGeoModel);
                        }
                        else
                        {
                            iFirebaseFailedListener.onFirebaseLoadFailed(getString(R.string.not_found_key)+driverGeoModel.getKey());

                        }

                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        iFirebaseFailedListener .onFirebaseLoadFailed(databaseError.getMessage());
                    }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        //Mevcut konumu eklemek için izin iste
        Dexter.withContext(getContext())
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                            return;
                        }
                        mMap.setMyLocationEnabled(true);
                        mMap.getUiSettings().setMyLocationButtonEnabled(true);

                        mMap.setOnMyLocationButtonClickListener(() -> {
                            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                                    && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                return false;
                            }
                            fusedLocationProviderclient.getLastLocation()
                                    .addOnFailureListener(e -> Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT)
                                            .show())
                                    .addOnSuccessListener(location -> {

                                        LatLng userLatLng = new LatLng (location.getLatitude(), location.getLongitude());
                                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,18f));

                                    });
                            return true;
                        });



                                //Layout button
                                View locationButton = ((View) mapFragment.getView().findViewById(Integer.parseInt("1")).getParent())
                                        .findViewById(Integer.parseInt("2"));
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
                        //Right bottom
                        params.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
                        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
                        params.setMargins(0, 0, 0, 250);// zoom kontrol


                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        Snackbar.make(getView(), permissionDeniedResponse.getPermissionName() + "İzin veriniz.",
                                Snackbar.LENGTH_SHORT).show();

                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {

                    }
                })
                .check();

        mMap.getUiSettings().setZoomControlsEnabled(true);

        try {
            boolean success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(getContext(),
                    R.raw.maps_style));
            if (!success)
                Snackbar.make(getView(), "Harita stili yüklenemedi", Snackbar.LENGTH_SHORT).show();
        } catch (Exception e) {
            Snackbar.make(getView(), e.getMessage(), Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onFirebaseLoadFailed(String message) {
        Snackbar.make(getView(),message,Snackbar.LENGTH_SHORT).show();

    }

    @Override
    public void onDriverInfoLoadSuccess(DriverGeoModel driverGeoModel) {
        if(!Common.markerList.containsKey(driverGeoModel.getKey()))
            Common.markerList.put(driverGeoModel.getKey(),
                    mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(driverGeoModel.getGeoLocation().latitude,
                                    driverGeoModel.getGeoLocation().longitude))
                                    .flat(true)
                                    .title(Common.buildName(driverGeoModel.getDriverInfoModel().getFirstName (),
                                            driverGeoModel.getDriverInfoModel().getLastName()))
                                    .snippet (driverGeoModel.getDriverInfoModel().getPhoneNumber ())
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))));
        if(!TextUtils.isEmpty(cityName)) {
            DatabaseReference driverLocation = FirebaseDatabase.getInstance()
                    .getReference(Common.DRIVERS_LOCATION_REFERENCES)
                    .child(cityName)
                    .child(driverGeoModel.getKey());
            driverLocation.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if(!dataSnapshot.hasChildren())
                    {
                        if(Common.markerList.get(driverGeoModel.getKey()) != null)
                            Common.markerList.get(driverGeoModel.getKey()).remove(); //Remove marker
                        Common.markerList.remove(driverGeoModel.getKey()); //Remove marker info from hash map
                        driverLocation.removeEventListener(this); //Remove event listener

                    }

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Snackbar.make (getView(), databaseError.getMessage (), Snackbar.LENGTH_SHORT).show();


                }
            });
        }
    }
}