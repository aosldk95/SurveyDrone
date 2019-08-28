package com.example.SurveyDrone;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolygonOverlay;
import com.naver.maps.map.util.FusedLocationSource;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.MissionApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.mission.Mission;
import com.o3dr.services.android.lib.drone.mission.item.spatial.Waypoint;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.gcs.link.LinkConnectionStatus;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private FusedLocationSource locationSource;

    MapFragment mNaverMapFragment = null;
    LatLng ClickLatLng = null;

    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;

    private Spinner modeSelector;

    NaverMap naverMap;

    private final Handler handler = new Handler();

    List<Marker> markers = new ArrayList<>();

    private int Marker_Count = 0;

    public static StringBuilder sb;

    String pnu = "";
    String ag_geom = "";
    String TextAddress = "";
    // pnu로 받아온 좌표값 저장
    List<LatLng> Coords = new ArrayList<>();
    PolygonOverlay polygon = new PolygonOverlay();
    ArrayList<String> recycler_list = new ArrayList<>();    // 리사이클러뷰
    List<LocalTime> recycler_time = new ArrayList<>();      // 리사이클러뷰 시간

    private int InsertedNumber = 0;

    protected double mRecentAltitude = 0;
    private int Reached_Count = 1;
    public int takeOffAltitude = 3;
    private int Recycler_Count = 0;

    Marker MarkerWhereToGo = new Marker();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Start mainActivity");
        super.onCreate(savedInstanceState);
        // 소프트바 지우기
        deleteStatusBar();
        // 상태바 지우기
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        // 지도 띄우기
        FragmentManager fm = getSupportFragmentManager();
        mNaverMapFragment = (MapFragment) fm.findFragmentById(R.id.map);
        if (mNaverMapFragment == null) {
            mNaverMapFragment = MapFragment.newInstance();
            fm.beginTransaction().add(R.id.map, mNaverMapFragment).commit();
        }

        // 모드 변경 스피너
        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ((TextView) parent.getChildAt(0)).setTextColor(Color.WHITE);
                onFlightModeSelected(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> prent) {
                // Do nothing
            }
        });

        // 내 위치
        locationSource =
                new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);


        mNaverMapFragment.getMapAsync(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,  @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            return;
        }
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        // 클릭 이벤트 리스너
        naverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {

                Button BtnSendMission = (Button) findViewById(R.id.BtnSendMission);
                if(BtnSendMission.getVisibility()==View.INVISIBLE) {
                    BtnSendMission.setVisibility(View.VISIBLE);
                }
                if(Coords.size() == 0) {
                    NaverReverseGeocoding(latLng);
                }
            }
        });

        // 내 위치
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.NoFollow);

        // 켜지자마자 드론 연결
        ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
        this.drone.connect(params);

        UiSettings uiSettings = naverMap.getUiSettings();

        // 네이버 로고 위치 변경
        uiSettings.setLogoMargin(2080, 0, 0, 150);

        // 줌 버튼 제거
        uiSettings.setZoomControlEnabled(false);

        // 축척 바 제거
        uiSettings.setScaleBarEnabled(false);

        // 이륙고도 표시
        ShowTakeOffAltitude();

        // UI상 버튼 제어
        ControlButton();


        Log.d("MapLog", "ClickLatLng : " + ClickLatLng);
    }

    private void deleteStatusBar() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
        );
    }

    public void ControlButton() {
        // 기본 UI 4개 버튼
        final Button BtnMapMoveLock = (Button) findViewById(R.id.BtnMapMoveLock);
        final Button BtnMapType = (Button) findViewById(R.id.BtnMapType);
        final Button BtnLandRegistrationMap = (Button) findViewById(R.id.BtnLandRegistrationMap);
        final Button BtnClear = (Button) findViewById(R.id.BtnClear);
        // Map 잠금 버튼
        final Button MapMoveLock = (Button) findViewById(R.id.MapMoveLock);
        final Button MapMoveUnLock = (Button) findViewById(R.id.MapMoveUnLock);
        // Map Type 버튼
        final Button MapType_Basic = (Button) findViewById(R.id.MapType_Basic);
        final Button MapType_Terrain = (Button) findViewById(R.id.MapType_Terrain);
        final Button MapType_Satellite = (Button) findViewById(R.id.MapType_Satellite);
        // 지적도 버튼
        final Button LandRegistrationOn = (Button) findViewById(R.id.LandRegistrationOn);
        final Button LandRegistrationOff = (Button) findViewById(R.id.LandRegistrationOff);
        // 이륙고도 버튼
        final Button BtnTakeOffAltitude = (Button) findViewById(R.id.BtnTakeOffAltitude);
        final Button TakeOffUp = (Button) findViewById(R.id.TakeOffUp);
        final Button TakeOffDown = (Button) findViewById(R.id.TakeOffDown);

        TextView textViewAddress = (TextView) findViewById(R.id.textViewAddress);

        final Button BtnSendMission = (Button) findViewById(R.id.BtnSendMission);

        final UiSettings uiSettings = naverMap.getUiSettings();

        // ############################## 기본 UI 버튼 제어 #######################################
        // 맵 이동 / 맵 잠금
        BtnMapMoveLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (MapType_Satellite.getVisibility() == view.VISIBLE) {
                    MapType_Basic.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (LandRegistrationOn.getVisibility() == view.VISIBLE) {
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                }

                if (MapMoveLock.getVisibility() == view.INVISIBLE) {
                    MapMoveLock.setVisibility(View.VISIBLE);
                    MapMoveUnLock.setVisibility(View.VISIBLE);
                } else if (MapMoveLock.getVisibility() == view.VISIBLE) {
                    MapMoveLock.setVisibility(View.INVISIBLE);
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                }
            }
        });

        // 지도 모드
        BtnMapType.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (MapMoveUnLock.getVisibility() == view.VISIBLE) {
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                    MapMoveLock.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (LandRegistrationOn.getVisibility() == view.VISIBLE) {
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                }
                if (MapType_Satellite.getVisibility() == view.INVISIBLE) {
                    MapType_Satellite.setVisibility(View.VISIBLE);
                    MapType_Terrain.setVisibility(View.VISIBLE);
                    MapType_Basic.setVisibility(View.VISIBLE);
                } else {
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Basic.setVisibility(View.INVISIBLE);
                }
            }
        });

        // 지적도
        BtnLandRegistrationMap.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (MapType_Satellite.getVisibility() == view.VISIBLE) {
                    MapType_Basic.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (MapMoveUnLock.getVisibility() == view.VISIBLE) {
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                    MapMoveLock.setVisibility(View.INVISIBLE);
                }

                if (LandRegistrationOff.getVisibility() == view.INVISIBLE) {
                    LandRegistrationOff.setVisibility(View.VISIBLE);
                    LandRegistrationOn.setVisibility(View.VISIBLE);
                } else {
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                }
            }
        });

        // ############################### 맵 이동 관련 제어 ######################################
        // 맵잠금
        MapMoveLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapMoveUnLock.setBackgroundResource(R.drawable.mybutton_dark);
                MapMoveLock.setBackgroundResource(R.drawable.mybutton);

                BtnMapMoveLock.setText("맵 잠금");

                uiSettings.setScrollGesturesEnabled(false);

                MapMoveLock.setVisibility(View.INVISIBLE);
                MapMoveUnLock.setVisibility(View.INVISIBLE);
            }
        });

        // 맵 이동
        MapMoveUnLock.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapMoveUnLock.setBackgroundResource(R.drawable.mybutton);
                MapMoveLock.setBackgroundResource(R.drawable.mybutton_dark);

                BtnMapMoveLock.setText("맵 이동");

                uiSettings.setScrollGesturesEnabled(true);

                MapMoveLock.setVisibility(View.INVISIBLE);
                MapMoveUnLock.setVisibility(View.INVISIBLE);
            }
        });

        // ################################## 지도 모드 제어 ######################################

        // 위성 지도
        MapType_Satellite.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 색 지정
                MapType_Satellite.setBackgroundResource(R.drawable.mybutton);
                MapType_Basic.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Terrain.setBackgroundResource(R.drawable.mybutton_dark);

                BtnMapType.setText("위성지도");

                naverMap.setMapType(NaverMap.MapType.Satellite);

                // 다시 닫기
                MapType_Satellite.setVisibility(View.INVISIBLE);
                MapType_Terrain.setVisibility(View.INVISIBLE);
                MapType_Basic.setVisibility(View.INVISIBLE);
            }
        });

        // 지형도
        MapType_Terrain.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 색 지정
                MapType_Satellite.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Basic.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Terrain.setBackgroundResource(R.drawable.mybutton);

                BtnMapType.setText("지형도");

                naverMap.setMapType(NaverMap.MapType.Terrain);

                MapType_Satellite.setVisibility(View.INVISIBLE);
                MapType_Terrain.setVisibility(View.INVISIBLE);
                MapType_Basic.setVisibility(View.INVISIBLE);
            }
        });

        // 일반지도
        MapType_Basic.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                MapType_Satellite.setBackgroundResource(R.drawable.mybutton_dark);
                MapType_Basic.setBackgroundResource(R.drawable.mybutton);
                MapType_Terrain.setBackgroundResource(R.drawable.mybutton_dark);

                BtnMapType.setText("일반지도");

                naverMap.setMapType(NaverMap.MapType.Basic);

                MapType_Satellite.setVisibility(View.INVISIBLE);
                MapType_Terrain.setVisibility(View.INVISIBLE);
                MapType_Basic.setVisibility(View.INVISIBLE);
            }
        });

        // ################################ 지적도 On / Off 제어 ##################################

        LandRegistrationOn.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                LandRegistrationOn.setBackgroundResource(R.drawable.mybutton);
                LandRegistrationOff.setBackgroundResource(R.drawable.mybutton_dark);

                BtnLandRegistrationMap.setText("지적도 ON");

                naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, true);

                LandRegistrationOn.setVisibility(View.INVISIBLE);
                LandRegistrationOff.setVisibility(View.INVISIBLE);
            }
        });

        LandRegistrationOff.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                LandRegistrationOn.setBackgroundResource(R.drawable.mybutton_dark);
                LandRegistrationOff.setBackgroundResource(R.drawable.mybutton);

                BtnLandRegistrationMap.setText("지적도 OFF");

                naverMap.setLayerGroupEnabled(NaverMap.LAYER_GROUP_CADASTRAL, false);

                LandRegistrationOn.setVisibility(View.INVISIBLE);
                LandRegistrationOff.setVisibility(View.INVISIBLE);
            }
        });

        // ###################################### Clear ###########################################
        BtnClear.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (MapMoveUnLock.getVisibility() == view.VISIBLE) {
                    MapMoveUnLock.setVisibility(View.INVISIBLE);
                    MapMoveLock.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (MapType_Satellite.getVisibility() == view.VISIBLE) {
                    MapType_Basic.setVisibility(View.INVISIBLE);
                    MapType_Terrain.setVisibility(View.INVISIBLE);
                    MapType_Satellite.setVisibility(View.INVISIBLE);
                }
                // 열려있으면 닫기
                if (LandRegistrationOn.getVisibility() == view.VISIBLE) {
                    LandRegistrationOn.setVisibility(View.INVISIBLE);
                    LandRegistrationOff.setVisibility(View.INVISIBLE);
                }

                polygon.setMap(null);

                Reached_Count = 1;

                Coords.clear();
                ag_geom = "";
                InsertedNumber = 0;
                pnu = "";

                // 텍스트뷰 textViewAddress 제거
                textViewAddress.setText("");
                TextAddress = "";

                // 임무 전송 버튼 invisible
                if (BtnSendMission.getVisibility() == View.VISIBLE) {
                    BtnSendMission.setVisibility(View.INVISIBLE);
                }
                BtnSendMission.setText("임무 전송");

                MarkerWhereToGo.setMap(null);
            }
        });

        // ###################################### 이륙 고도 설정 #################################
        // 이륙고도 버튼
        BtnTakeOffAltitude.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 열려있으면 닫기
                if (TakeOffUp.getVisibility() == view.VISIBLE) {
                    TakeOffUp.setVisibility(View.INVISIBLE);
                    TakeOffDown.setVisibility(View.INVISIBLE);
                } else if (TakeOffUp.getVisibility() == view.INVISIBLE) {
                    TakeOffUp.setVisibility(View.VISIBLE);
                    TakeOffDown.setVisibility(View.VISIBLE);
                }
            }
        });

        // 이륙고도 Up 버튼
        TakeOffUp.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                takeOffAltitude += 1;
                ShowTakeOffAltitude();
            }
        });

        // 이륙고도 Down 버튼
        TakeOffDown.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                takeOffAltitude -= 1;
                ShowTakeOffAltitude();
            }
        });

        // ###################################### 미션 전송 #######################################

        BtnSendMission.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (BtnSendMission.getText().equals("임무 전송")) {
                    // waypoint 생성, 미션 전송
                    MakeWayPoint();
                } else if (BtnSendMission.getText().equals("임무 시작")) {
                    // Auto 모드로 전환
                    ChangeToAutoMode();
                    BtnSendMission.setText("임무 중지");
                } else if (BtnSendMission.getText().equals("임무 중지")) {
                    // Loiter 모드로 전환
                    ChangeToLoiterMode();
                    BtnSendMission.setText("임무 재시작");
                } else if (BtnSendMission.getText().equals("임무 재시작")) {
                    ChangeToAutoMode();
                    BtnSendMission.setText("임무 중지");
                }
            }
        });
    }

    @Override
    public void onDroneEvent(String event, Bundle extras) {
        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                alertUser("드론이 연결되었습니다.");
                break;

            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("드론이 연결 해제되었습니다.");
                break;

            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;

            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;

            case AttributeEvent.GPS_POSITION:
                SetDronePosition();
                break;

            case AttributeEvent.SPEED_UPDATED:
                SpeedUpdate();
                break;

            case AttributeEvent.ALTITUDE_UPDATED:
                AltitudeUpdate();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                BatteryUpdate();
                break;

            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                ArmBtnUpdate();
                break;

            case AttributeEvent.ATTITUDE_UPDATED:
                UpdateYaw();
                break;

            case AttributeEvent.GPS_COUNT:
                ShowSatelliteCount();
                break;

            case AttributeEvent.MISSION_SENT:
                Mission_Send();
                ShowWhereToGo(0);
                break;

            case AttributeEvent.MISSION_ITEM_REACHED:
                MarkerWhereToGo.setMap(null);
                alertUser(Reached_Count + "번 waypoint 도착 : " + Reached_Count + "/" + Coords.size() );
                ShowWhereToGo(Reached_Count);
                Reached_Count++;
                break;

            default:
                MakePolygon();
                MakeRecyclerView();
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void ShowWhereToGo(int number) {
        MarkerWhereToGo.setPosition(new LatLng(Coords.get(number).latitude,Coords.get(number).longitude));
        MarkerWhereToGo.setWidth(60);
        MarkerWhereToGo.setHeight(80);
        MarkerWhereToGo.setMap(naverMap);
    }

    // ################################## 비행 모드 변경 ##########################################

    private void ChangeToAutoMode() {
        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_AUTO, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Auto 모드로 변경 중...");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Auto 모드 변경 실패 : " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Auto 모드 변경 실패.");
            }
        });
    }

    private void ChangeToLoiterMode() {
        VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LOITER, new SimpleCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Loiter 모드로 변경 중...");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Loiter 모드 변경 실패 : " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Loiter 모드 변경 실패");
            }
        });
    }

    // ######################################## Http 통신 #########################################

    private String getTagValue(String tag, Element element) {
        NodeList nList = element.getElementsByTagName(tag).item(0).getChildNodes();

        Node nValue = (Node) nList.item(0);
        if (nValue == null) {
            return null;
        }
        return nValue.getNodeValue();
    }

    private void NaverReverseGeocoding(LatLng latLng) {

        new Thread() {
            @Override
            public void run() {
                String clientId = "6vmnkggakc";
                String clientSecret = "OakkUJ1OhSA8ad2T0wJ9NgbqFOuDQxzjniePHGcV";

                double x = latLng.longitude;
                double y = latLng.latitude;

                try {
                    String apiURL = "https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc?coords=" + x + "," + y + "&sourcecrs=EPSG:4019&orders=addr";

                    Log.d("NaverReverseGeocoding", "apiURL : " + apiURL);

                    URL url = new URL(apiURL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("X-NCP-APIGW-API-KEY-ID", clientId);
                    conn.setRequestProperty("X-NCP-APIGW-API-KEY", clientSecret);

                    // #############################################################
//                  Naver XML 문 확인 코드 (아래 XML 구문 전체 주석처리 후 사용)

//                    int responseCode = conn.getResponseCode();
//
//                  BufferedReader br;
//                    if(responseCode == 200) {
//                        br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//                    } else {
//                        br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
//                    }
//
//                    sb = new StringBuilder();
//                    String line;
//
//                    while((line = br.readLine()) != null) {
//                        sb.append(line + "\n");
//                    }
//
//                    Log.d("NaverReverseGeocoding", "sb : " + sb);

                    // #############################################################
                    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                    XmlPullParser xpp = factory.newPullParser();
                    String tag;
                    // inputStream으로부터 xml 값 받기
                    xpp.setInput(conn.getInputStream(), null);
                    xpp.next();
                    int eventType = xpp.getEventType();

                    while (eventType != XmlPullParser.END_DOCUMENT) {       // 문서가 끝나지 않을때까지
                        switch (eventType) {
                            case XmlPullParser.START_DOCUMENT:              // 문서 시작 시
                                break;                                      // 아무 일도 하지 않음

                            case XmlPullParser.START_TAG:                   // 태그 시작 시
                                tag = xpp.getName();                        // 태그 값을 tag 변수에 저장

                                String zero = "";

                                if (tag.equals("id")) {                     // 태그 값이 id 일때
                                    xpp.next();
                                    pnu = xpp.getText();                    // pnu 에 값 저장
                                    Log.d("NaverReverseGeocoding", "pnu1 : " + pnu);
                                    break;
                                }
                                if (tag.equals("land")) {                   // 태그 값이 land 일때
                                    xpp.next();
                                    tag = xpp.getName();                    // 다음 태그값을 받음
                                    if (tag.equals("type")) {               // 태그 값이 type 일때
                                        xpp.next();
                                        pnu = pnu + xpp.getText();          // pnu에 값 추가 저장
                                        Log.d("NaverReverseGeocoding", "pnu2 : " + pnu);
                                    }
                                }
                                if (tag.equals("number1")) {
                                    xpp.next();
                                    xpp.getText().length();
                                    for (int i = 0; i < (4 - xpp.getText().length()); i++) {
                                        zero = zero + "0";
                                    }
                                    pnu = pnu + zero + xpp.getText();
                                    Log.d("NaverReverseGeocoding", "pnu3 : " + pnu);

                                    TextAddress = TextAddress + xpp.getText();
                                    Log.d("NaverReverseGeocoding", "TextAddress5 : " + TextAddress);
                                }
                                if (tag.equals("number2")) {
                                    xpp.next();
                                    if (xpp.getText() != null) {
                                        xpp.getText().length();

                                        for (int i = 0; i < (4 - xpp.getText().length()); i++) {
                                            zero = zero + "0";
                                        }
                                        pnu = pnu + zero + xpp.getText();
                                        Log.d("NaverReverseGeocoding", "pnu4 : " + pnu);

                                        TextAddress = TextAddress + "-" + xpp.getText() + "번지";
                                        Log.d("NaverReverseGeocoding", "TextAddress6 : " + TextAddress);
                                    } else {
                                        pnu = pnu + "0000";
                                        Log.d("NaverReverseGeocoding", "pnu4 : " + pnu);

                                        TextAddress = TextAddress + "번지";
                                        Log.d("NaverReverseGeocoding", "TextAddress6 : " + TextAddress);
                                    }
                                }

                                if(tag.equals("area1")) {
                                    xpp.next();
                                    tag = xpp.getName();
                                    if(tag.equals("name")) {
                                        xpp.next();
                                        TextAddress = TextAddress + xpp.getText() + " ";
                                        Log.d("NaverReverseGeocoding", "TextAddress1 : " + TextAddress);
                                    }
                                }
                                if(tag.equals("area2")) {
                                    xpp.next();
                                    tag = xpp.getName();
                                    if(tag.equals("name")) {
                                        xpp.next();
                                        TextAddress = TextAddress + xpp.getText() + " ";
                                        Log.d("NaverReverseGeocoding", "TextAddress2 : " + TextAddress);
                                    }
                                }
                                if(tag.equals("area3")) {
                                    xpp.next();
                                    tag = xpp.getName();
                                    if(tag.equals("name")) {
                                        xpp.next();
                                        TextAddress = TextAddress + xpp.getText() + " ";
                                        Log.d("NaverReverseGeocoding", "TextAddress3 : " + TextAddress);
                                    }
                                }
                                if(tag.equals("area4")) {
                                    xpp.next();
                                    if(xpp.getName() != null) {
                                        tag = xpp.getName();
                                        if(tag.equals("name")) {
                                            xpp.next();
                                            TextAddress = TextAddress + xpp.getText() + " ";
                                            Log.d("NaverReverseGeocoding", "TextAddress4 : " + TextAddress);
                                        }
                                    }
                                }

                        }
                        eventType = xpp.next();
                    }

                    TextView textViewAddress = (TextView) findViewById(R.id.textViewAddress);
                    textViewAddress.setText(TextAddress);

                    // pnu -> polygon 좌표들
                    VworldDataAPI();

                } catch (ProtocolException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void VworldDataAPI() {
        new Thread() {
            @Override
            public void run() {
                try {
                    String Key = "A491D6DA-366E-39F7-8BFF-09455B6A3E1D";
                    String Domain = "http://localhost:8080";

                    String apiURL2 = "https://api.vworld.kr/req/data?service=data&request=GetFeature&key=" + Key +
                            "&domain=" + Domain + "&format=xml&data=LP_PA_CBND_BUBUN&EPSG:4019&attrFilter=pnu:=:" + pnu;

                    Log.d("VworldDataAPI", "VworldDataAPI : " + apiURL2);

                    // 문서를 읽기 위한 공장 만들기
                    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                    // 빌더 생성
                    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                    // 생성된 빌더를 통해서 xml 문서를 Document객체로 파싱해서 가져온다
                    Document doc = dBuilder.parse(apiURL2);
                    // 문서 구조 안정화
                    doc.getDocumentElement().normalize();

                    // XML 최상위 tag
                    Log.d("VworldDataAPI", "Root element : " + doc.getDocumentElement().getNodeName());

                    Element root = doc.getDocumentElement();

                    Log.d("VworldDataAPI", "element : " + root);

                    NodeList list = root.getElementsByTagName("result");

                    Log.d("VworldDataAPI", "list : " + list);

                    for (int i = 0; i < list.getLength(); i++) {
                        Node node = list.item(i);

                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element element = (Element) node;
                            ag_geom = getTagValue("gml:posList", element);

                            Log.d("VworldDataAPI", "ag_geom : " + ag_geom);
                        }
                    }

                    String[] Coords_split = ag_geom.split("\\s");

                    for (int i = 0; i < Coords_split.length; i = i + 2) {
                        Coords.add(new LatLng(Double.parseDouble(Coords_split[i + 1]), Double.parseDouble(Coords_split[i])));
                    }

                } catch (Exception e) {

                }
            }
        }.start();
    }

    private void MakePolygon() {
        if((Coords.size() > 3) && (InsertedNumber == 0)) {
            polygon.setCoords(Coords);
            polygon.setColor(Color.YELLOW);
            polygon.setOutlineWidth(5);
            polygon.setOutlineColor(Color.RED);

            polygon.setMap(naverMap);

            InsertedNumber = 1;
        }
    }

    // ###################################### 미션 Mission ########################################

    private void MakeWayPoint() {
        Mission mMission = new Mission();

        for(int i=0; i<Coords.size(); i++) {
            Waypoint waypoint = new Waypoint();
            waypoint.setDelay(5);

            LatLongAlt latLongAlt = new LatLongAlt(Coords.get(i).latitude, Coords.get(i).longitude, mRecentAltitude);
            waypoint.setCoordinate(latLongAlt);

            mMission.addMissionItem(waypoint);
        }

        MissionApi.getApi(this.drone).setMission(mMission,true);
    }

    private void Mission_Send() {
        alertUser("미션 업로드 완료");
        Button BtnSendMission = (Button) findViewById(R.id.BtnSendMission);
        BtnSendMission.setText("임무 시작");
    }

    // ######################################## UI 바 #############################################

    private void UpdateYaw() {
        // Attitude 받아오기
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        double yaw = attitude.getYaw();

        // yaw 값을 양수로
        if ((int) yaw < 0) {
            yaw += 360;
        }

        // [UI] yaw 보여주기
        TextView textView_yaw = (TextView) findViewById(R.id.yaw);
        textView_yaw.setText("YAW " + (int) yaw + "deg");
    }

    private void ArmBtnUpdate() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button ArmBtn = (Button) findViewById(R.id.BtnArm);

        if (vehicleState.isFlying()) {
            // Land
            ArmBtn.setText("LAND");
        } else if (vehicleState.isArmed()) {
            // Take off
            ArmBtn.setText("TAKE OFF");
        } else if (vehicleState.isConnected()) {
            // Connected but not Armed
            ArmBtn.setText("ARM");
        }
    }

    private void BatteryUpdate() {
        TextView textView_Vol = (TextView) findViewById(R.id.Voltage);
        Battery battery = this.drone.getAttribute(AttributeType.BATTERY);
        double batteryVoltage = Math.round(battery.getBatteryVoltage() * 10) / 10.0;
        textView_Vol.setText("전압 " + batteryVoltage + "V");
        Log.d("Position8", "Battery : " + batteryVoltage);
    }

    private void AltitudeUpdate() {
        Altitude currentAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        mRecentAltitude = currentAltitude.getRelativeAltitude();
        double DoubleAltitude = (int) Math.round(mRecentAltitude*10)/10.0;

        TextView textView = (TextView) findViewById(R.id.Altitude);
//        Altitude altitude = this.drone.getAttribute(AttributeType.ALTITUDE);
//        int intAltitude = (int) Math.round(altitude.getAltitude());
        textView.setText("고도 " + DoubleAltitude + "m");
        Log.d("Position7", "Altitude : " + mRecentAltitude);
    }

    private void SpeedUpdate() {
        TextView textView = (TextView) findViewById(R.id.Speed);
        Speed speed = this.drone.getAttribute(AttributeType.SPEED);
        int doubleSpeed = (int) Math.round(speed.getGroundSpeed());
        // double doubleSpeed = Math.round(speed.getGroundSpeed()*10)/10.0; 소수점 첫째자리까지
        textView.setText("속도 " + doubleSpeed + "m/s");
        Log.d("Position6", "Speed : " + this.drone.getAttribute(AttributeType.SPEED));
    }

    public void SetDronePosition() {
        // 드론 위치 받아오기
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong dronePosition = droneGps.getPosition();

        Log.d("Position1", "droneGps : " + droneGps);
        Log.d("Position1", "dronePosition : " + dronePosition);

        // 이동했던 위치 맵에서 지워주기
        if (Marker_Count - 1 >= 0) {
            markers.get(Marker_Count - 1).setMap(null);
        }

        // 마커 리스트에 추가
        markers.add(new Marker(new LatLng(dronePosition.getLatitude(), dronePosition.getLongitude())));

        // yaw 에 따라 네비게이션 마커 회전
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        double yaw = attitude.getYaw();
        Log.d("Position4", "yaw : " + yaw);
        if ((int) yaw < 0) {
            yaw += 360;
        }
        markers.get(Marker_Count).setAngle((float) yaw);

        // 마커 크기 지정
        markers.get(Marker_Count).setHeight(400);
        markers.get(Marker_Count).setWidth(80);

        // 마커 아이콘 지정
        markers.get(Marker_Count).setIcon(OverlayImage.fromResource(R.drawable.marker_icon));

        // 마커 위치를 중심점으로 지정
        markers.get(Marker_Count).setAnchor(new PointF(0.5F, 0.9F));

        // 마커 띄우기
        markers.get(Marker_Count).setMap(naverMap);

        // 카메라 위치 설정
        Button BtnMapMoveLock = (Button) findViewById(R.id.BtnMapMoveLock);
        String text = (String) BtnMapMoveLock.getText();

        if (text.equals("맵 잠금")) {
            CameraUpdate cameraUpdate = CameraUpdate.scrollTo(new LatLng(dronePosition.getLatitude(), dronePosition.getLongitude()));
            naverMap.moveCamera(cameraUpdate);
        }
        Log.d("Position3", "markers.size() : " + markers.size());

        // [UI] 잡히는 GPS 개수
        ShowSatelliteCount();

        Marker_Count++;
    }

    private void ShowSatelliteCount() {
        // [UI] 잡히는 GPS 개수
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        int Satellite = droneGps.getSatellitesCount();
        TextView textView_gps = (TextView) findViewById(R.id.GPS_state);
        textView_gps.setText("위성 " + Satellite);
    }

    private void ShowTakeOffAltitude() {
        final Button BtnTakeOffAltitude = (Button) findViewById(R.id.BtnTakeOffAltitude);
        BtnTakeOffAltitude.setText(takeOffAltitude + " m\n이륙고도");
    }

    // ####################################### Connect ############################################

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
                alertUser("연결 실패 :" + msg);
                break;
        }
    }

    @Override
    public void onTowerConnected() {
        alertUser("드론-핸드폰 연결 성공.");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        alertUser("드론-핸드폰 연결 해제.");
    }

    @Override
    protected void onStart() {
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    // ###################################### 모드 변환 ###########################################

    protected void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    public void onFlightModeSelected(View view) {
        final VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("비행 모드 " + vehicleMode.toString() + "로 변경.");
            }

            @Override
            public void onError(int executionError) {
                alertUser("비행 모드 변경 실패 : " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("비행 모드 변경 시간 초과.");
            }
        });
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    // ###################################### Arming ##############################################

    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if (vehicleState.isFlying()) {
            // Land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError) {
                    alertUser("착륙할 수 없습니다 : " + executionError);
                }

                @Override
                public void onTimeout() {
                    alertUser("착륙할 수 없습니다.");
                }
            });
        } else if (vehicleState.isArmed()) {
            // Take off
            ControlApi.getApi(this.drone).takeoff(takeOffAltitude, new AbstractCommandListener() {
                @Override
                public void onSuccess() {
                    alertUser("이륙에 성공하였습니다.");
                }

                @Override
                public void onError(int executionError) {
                    alertUser("이륙 할 수 없습니다 : " + executionError);
                }

                @Override
                public void onTimeout() {
                    alertUser("이륙 할 수 없습니다.");
                }
            });
        } else if (!vehicleState.isConnected()) {
            // Connect
            alertUser("드론 연결을 하십시오.");
        } else {
            // Connected but not Armed
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Arming...");
            builder.setMessage("시동을 걸면 프로펠러가 고속으로 회전합니다.");
            builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Arming();
                }
            });
            builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        }
    }

    public void Arming() {
        VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
            @Override
            public void onError(int executionError) {
                alertUser("아밍 할 수 없습니다 " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("아밍 할 수 없습니다.");
            }
        });
    }

    // ###################################### 알림 ################################################

    private void alertUser(String message) {
//        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
//        Log.d(TAG, message);

        // 5개 이상 삭제
        if(recycler_list.size() > 3) {
            recycler_list.remove(Recycler_Count);
        }

        LocalTime localTime = LocalTime.now();
        recycler_list.add(String.format("  ★  " + message));
        recycler_time.add(localTime);

        // 리사이클러뷰에 LinearLayoutManager 객체 지정.
        RecyclerView recyclerView = findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 리사이클러뷰에 SimpleAdapter 객체 지정.
        SimpleTextAdapter adapter = new SimpleTextAdapter(recycler_list);
        recyclerView.setAdapter(adapter);
    }

    // ################################ 리사이클러뷰 RecyclerView #################################

    private void MakeRecyclerView() {
        LocalTime localTime = LocalTime.now();

        // recyclerView 시간 지나면 제거
        if (recycler_list.size() > 0) {
            Log.d("Position2","---------------------------------------------------");
            Log.d("Position2","[Minute] recycler time : " + recycler_time.get(Recycler_Count).getMinute());
            Log.d("Position2","[Minute] Local time : " + localTime.getMinute());
            if (recycler_time.get(Recycler_Count).getMinute() == localTime.getMinute()) {
                Log.d("Position2", "recycler time : " + recycler_time.get(Recycler_Count).getSecond());
                Log.d("Position2", "Local time : " + localTime.getSecond());
                Log.d("Position2", "[★] recycler size() : " + recycler_list.size());
                Log.d("Position2", "[★] Recycler_Count : " + Recycler_Count);
                if (localTime.getSecond() >= recycler_time.get(Recycler_Count).getSecond() + 3) {
                    RemoveRecyclerView();
                }
            } else {
                // 3초가 지났을 때 1분이 지나감
                Log.d("Position2", "recycler time : " + recycler_time.get(Recycler_Count).getSecond());
                Log.d("Position2", "Local time : " + localTime.getSecond());
                Log.d("Position2", "[★] recycler size() : " + recycler_list.size());
                Log.d("Position2", "[★] Recycler_Count : " + Recycler_Count);
                if (localTime.getSecond() + 60 >= recycler_time.get(Recycler_Count).getSecond() + 3) {
                    RemoveRecyclerView();
                }
            }
            Log.d("Position2","---------------------------------------------------");
        }
    }

    private void RemoveRecyclerView() {
        recycler_list.remove(Recycler_Count);
        recycler_time.remove(Recycler_Count);
        if(recycler_list.size() > Recycler_Count) {
            LocalTime localTime = LocalTime.now();
            recycler_time.set(Recycler_Count,localTime);
        }

        RecyclerView recyclerView = findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        SimpleTextAdapter adapter = new SimpleTextAdapter(recycler_list);
        recyclerView.setAdapter(adapter);
    }
}
