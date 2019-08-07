package com.example.mygcs;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.NaverMapOptions;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int DEFAULT_ZOOM = 18;

    MapFragment mNaverMapFragment = null;
    protected Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();
    protected NaverMap mMap;
    protected ArrayList<LatLng> mListFlightHistory;
    private UpdateDroneState mUpdateDroneState;

    //현재 기체 상태값들 관리
    protected LatLng mDronePosition;
    protected double mDroneYaw;
    protected double mDroneAltitude;

    //상태값 표시하는 텍스트뷰 관리
    protected TextView txtBattery;
    protected TextView txtAltitude;
    protected TextView txtDistance;
    protected TextView txtMode;
    protected TextView txtSatellite;
    protected TextView txtSpeed;
    protected TextView txtYaw;
    protected Spinner modeSelector;

    //overlay(오버레이)들 관리
    protected Marker mDroneMarker;
    LocationOverlay locationOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        //설정 제목 없음
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //전체 화면 설정
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        FragmentManager fm = getSupportFragmentManager();
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            NaverMapOptions options = new NaverMapOptions()
                    .camera(new CameraPosition(new LatLng(35.1798159, 129.0750222), DEFAULT_ZOOM))
                    .mapType(NaverMap.MapType.Satellite);
            mNaverMapFragment = MapFragment.newInstance(options);
            fm.beginTransaction().add(R.id.map, mNaverMapFragment).commit();
        }

        mNaverMapFragment.getMapAsync(this);
        initViews();
        initOverlay();
        mUpdateDroneState = new UpdateDroneState(this);
        mListFlightHistory = new ArrayList<>();
    }

    private void initViews() {
        txtBattery = findViewById(R.id.txtBattery);
        txtAltitude = findViewById(R.id.txtAltitude);
        txtDistance = findViewById(R.id.txtDistance);
        txtSatellite = findViewById(R.id.txtSatellite);
        txtSpeed = findViewById(R.id.txtSpeed);
        txtYaw = findViewById(R.id.txtYaw);

        modeSelector = findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                onFlightModeSelected(view);
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    private void initOverlay() {
        //사용할 overlay(오버레이)들 초기화
        mDroneMarker = new Marker();
        mDroneMarker.setIcon(OverlayImage.fromResource(R.drawable.drone));
        mDroneMarker.setAnchor(new PointF(0.5f, 0.5f));
    }

    public void onFlightModeSelected(View view) {
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out.");
            }
        });
    }


    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    public void onStop() {
        super.onStop();
//        if (this.drone.isConnected()) {
//            this.drone.disconnect();
//        }
//
//        this.controlTower.unregisterDrone(this.drone);
//        this.controlTower.disconnect();
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.BATTERY_UPDATED:
                mUpdateDroneState.updateBattery();
                break;
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone Disconnected");
                //updateArmButton();
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                mUpdateDroneState.updateVehicleMode();
                break;

            case AttributeEvent.GPS_POSITION:
                mUpdateDroneState.updateGps();
                mUpdateDroneState.updateDistanceFromHome();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                mUpdateDroneState.updateAttitude();
                break;


            case AttributeEvent.GPS_COUNT:
                mUpdateDroneState.updateGpsCount();
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                mUpdateDroneState.updateAltitude();
                mUpdateDroneState.updateDistanceFromHome();
                break;

            case AttributeEvent.HOME_UPDATED:
                mUpdateDroneState.updateDistanceFromHome();
                break;

            case AttributeEvent.SPEED_UPDATED:
                mUpdateDroneState.updateSpeed();
                break;

            default:
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    public void connectDrone() {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
            this.drone.connect(params);
        }
    }


    @Override
    public void onDroneServiceInterrupted(String errorMsg) {

    }

    @Override
    public void onLinkStateUpdated(@NonNull LinkConnectionStatus connectionStatus) {
        switch (connectionStatus.getStatusCode()) {
            case LinkConnectionStatus.FAILED:
                Bundle extras = connectionStatus.getExtras();
                String msg = null;
                if (extras != null) {
                    msg = extras.getString(LinkConnectionStatus.EXTRA_ERROR_MSG);
                }
                alertUser("Connection Failed:" + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser("DroneKit-Android Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("DroneKit-Android Interrupted");
    }

    protected void alertUser(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        mMap = naverMap;
        connectDrone();

        this.locationOverlay = mMap.getLocationOverlay();
        locationOverlay.setVisible(true);
        GcsLocation gcsLocation = new GcsLocation(this);
        gcsLocation.startTracking();
    }

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, R.layout.spinner_item_01, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(R.layout.spinner_item_01);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }
}
