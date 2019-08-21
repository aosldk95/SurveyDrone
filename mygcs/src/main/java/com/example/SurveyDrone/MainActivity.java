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
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.overlay.PolygonOverlay;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.LinkListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class MainActivity extends AppCompatActivity implements DroneListener, TowerListener, LinkListener, OnMapReadyCallback {
    private static final String TAG = MainActivity.class.getSimpleName();

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

    String Address = "";
    String pnu = "";
    String ag_geom = "";
    // pnu로 받아온 좌표값 저장
    List<LatLng> Coords = new ArrayList<>();
    PolygonOverlay polygon = new PolygonOverlay();

    private int InsertedNumber = 0;

    protected double mRecentAltitude = 0;

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

        mNaverMapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        // 켜지자마자 드론 연결
        ConnectionParameter params = ConnectionParameter.newUdpConnection(null);
        this.drone.connect(params);

        UiSettings uiSettings = naverMap.getUiSettings();

        // 줌 버튼 제거
        uiSettings.setZoomControlEnabled(false);

        // 축척 바 제거
        uiSettings.setScaleBarEnabled(false);

        // UI상 버튼 제어
        ControlButton();

        // TODO : Click Event Listener
        // 클릭 이벤트 리스너
        naverMap.setOnMapClickListener(new NaverMap.OnMapClickListener() {
            @Override
            public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
                // 좌표 -> 주소
//                VworldGeocoding(latLng);

                // 좌표 -> pnu
                NaverReverseGeocoding(latLng);
            }
        });
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

                // TODO : 폴리곤 지우기, recycler 초기화

                polygon.setMap(null);

                Coords.clear();
                ag_geom="";
                InsertedNumber = 0;
                pnu="";
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

            default:
                MakePolygon();
                // Log.i("DRONE_EVENT", event); //Uncomment to see events from the drone
                break;
        }
    }

    private void MakePolygon() {
        if((Coords.size() > 3) && (InsertedNumber == 0)) {
            polygon.setCoords(Coords);
            polygon.setColor(Color.GREEN);
            polygon.setOutlineWidth(5);
            polygon.setOutlineColor(Color.BLUE);

            polygon.setMap(naverMap);

            InsertedNumber = 1;
        }
    }

    // ######################################## Http 통신 #########################################

    private void VworldGeocoding(LatLng latLng) {
        new Thread() {
            @Override
            public void run() {
                try {
                    String Key = "A491D6DA-366E-39F7-8BFF-09455B6A3E1D";

                    double x = latLng.longitude;
                    double y = latLng.latitude;

                    String apiURL = "https://api.vworld.kr/req/address?service=address&request=GetAddress&key=" + Key + "&point=" + x + "," + y + "&type=both&crs=\tEPSG:5179&format=xml";
                    Log.d("checkURL", "latLng : " + latLng);
                    Log.d("checkURL", "apiURL : " + apiURL);

                    // 문서를 읽기 위한 공장 만들기
                    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                    // 빌더 생성
                    DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                    // 생성된 빌더를 통해서 xml 문서를 Document객체로 파싱해서 가져온다
                    Document doc = dBuilder.parse(apiURL);
                    // 문서 구조 안정화
                    doc.getDocumentElement().normalize();

                    // XML 최상위 tag
                    Log.d("checkTag", "Root element : " + doc.getDocumentElement().getNodeName());

                    Element root = doc.getDocumentElement();

                    NodeList list = root.getElementsByTagName("result");

                    Log.d("checkTag", "파싱할 리스트 수 : " + list.getLength());

                    for (int i = 0; i < list.getLength(); i++) {
                        Node node = list.item(i);

                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            Element element = (Element) node;
                            Address = getTagValue("text", element);

                            Log.d("checkTag", "Address : " + Address);
                        }
                    }

                    NaverReverseGeocoding(latLng);

                } catch (ParserConfigurationException e) {
                    Log.d("checkURL", "ParserConfigurationException");
                    e.printStackTrace();
                } catch (SAXException e) {
                    Log.d("checkURL", "SAXEception");
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.d("checkURL", "IOException");
                    e.printStackTrace();
                }
            }
        }.start();
    }

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
                    String apiURL = "https://naveropenapi.apigw.ntruss.com/map-reversegeocode/v2/gc?coords=" + x + "," + y + "&sourcecrs=EPSG:4326&orders=addr";

                    Log.d("NaverReverseGeocoding", "apiURL : " + apiURL);

                    URL url = new URL(apiURL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("X-NCP-APIGW-API-KEY-ID", clientId);
                    conn.setRequestProperty("X-NCP-APIGW-API-KEY", clientSecret);

                    Log.d("NaverReverseGeocoding", "success");

                    // #############################################################
                    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                    XmlPullParser xpp = factory.newPullParser();
                    String tag;
                    // inputStream으로부터 xml 값 받기
                    xpp.setInput(conn.getInputStream(), null);
                    xpp.next();
                    int eventType = xpp.getEventType();

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        switch (eventType) {
                            case XmlPullParser.START_DOCUMENT:
                                break;

                            case XmlPullParser.START_TAG:
                                tag = xpp.getName();

                                String zero = "";

                                if (tag.equals("id")) {
                                    xpp.next();
                                    pnu = xpp.getText();
                                    Log.d("NaverReverseGeocoding", "pnu1 : " + pnu);
                                    break;
                                }
                                if (tag.equals("land")) {
                                    xpp.next();
                                    tag = xpp.getName();
                                    if (tag.equals("type")) {
                                        xpp.next();
                                        pnu = pnu + xpp.getText();
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
                                    } else {
                                        pnu = pnu + "0000";
                                        Log.d("NaverReverseGeocoding", "pnu4 : " + pnu);
                                    }
                                }
                        }
                        eventType = xpp.next();
                    }

                    // pnu -> polygon 좌표들
//                    VworldDataAPI();

                    String Key = "A491D6DA-366E-39F7-8BFF-09455B6A3E1D";
                    String Domain = "http://localhost:8080";

                    String apiURL2 = "https://api.vworld.kr/req/data?service=data&request=GetFeature&key=" + Key + "&domain=" + Domain + "&format=xml&data=LP_PA_CBND_BUBUN&attrFilter=pnu:=:" + pnu;

                    Log.d("VworldDataAPI", "VworldDataAPI : " + apiURL);

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

                    for(int i = 0; i < Coords_split.length; i = i + 2) {
                        Coords.add(new LatLng(Double.parseDouble(Coords_split[i+1]), Double.parseDouble(Coords_split[i])));
                    }

                    // #############################################################

//                    int responseCode = conn.getResponseCode();
//
//                    BufferedReader br;
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
//
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
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                } catch (SAXException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void VworldDataAPI() {
//        new Thread() {
//            @Override
//            public void run() {
        try {


        } catch (Exception e) {

        }

//        }.start();



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
        int newIntAltitude = (int) Math.round(mRecentAltitude);

        TextView textView = (TextView) findViewById(R.id.Altitude);
//        Altitude altitude = this.drone.getAttribute(AttributeType.ALTITUDE);
//        int intAltitude = (int) Math.round(altitude.getAltitude());
        textView.setText("고도 " + newIntAltitude + "m");
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
            ControlApi.getApi(this.drone).takeoff(3, new AbstractCommandListener() {
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
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Log.d(TAG, message);
    }
}
