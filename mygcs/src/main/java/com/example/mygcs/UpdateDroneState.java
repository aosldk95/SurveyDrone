package com.example.mygcs;
import android.widget.ArrayAdapter;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.CameraAnimation;
import com.naver.maps.map.CameraUpdate;
import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.VehicleMode;

public class UpdateDroneState {
    private MainActivity mActivity;
    private Drone drone;

    public UpdateDroneState(MainActivity activity) {
        this.mActivity = activity;
        this.drone = activity.drone;
    }

    public void updateBattery() {
        Battery battery = this.drone.getAttribute(AttributeType.BATTERY);
        mActivity.txtBattery.setText(String.format("%.2f", battery.getBatteryVoltage()) + "V");
    }

    public void updateGpsCount() {
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        mActivity.txtSatellite.setText(String.valueOf(gps.getSatellitesCount()));
    }

    protected void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) mActivity.modeSelector.getAdapter();
        mActivity.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    protected void updateGps() {
        Gps gps = this.drone.getAttribute(AttributeType.GPS);
        LatLng mapCenterPosition = new LatLng(0, 0);
        if (gps.isValid()) {
            mActivity.mDronePosition = new LatLng(gps.getPosition().getLatitude(), gps.getPosition().getLongitude());
            mActivity.mListFlightHistory.add(mActivity.mDronePosition);
            mActivity.drawDroneMaker();
            mapCenterPosition = mActivity.mDronePosition;
        } else {
            mapCenterPosition = mActivity.locationOverlay.getPosition();
        }

        CameraUpdate cameraUpdate = CameraUpdate.scrollAndZoomTo(mapCenterPosition, mActivity.mMap.getCameraPosition().zoom).animate(CameraAnimation.Fly, 1000);
        mActivity.mMap.moveCamera(cameraUpdate);
    }

    protected void updateDistanceFromHome() {
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        double distanceFromHome = 0;

        if (droneGps.isValid()) {
            LatLongAlt vehicle3DPosition = new LatLongAlt(mActivity.mDronePosition.latitude, mActivity.mDronePosition.longitude, mActivity.mDroneAltitude);
            Home droneHome = this.drone.getAttribute(AttributeType.HOME);
            distanceFromHome = distanceBetweenPoints(droneHome.getCoordinate(), vehicle3DPosition);
        } else {
            distanceFromHome = 0;
        }

        mActivity.txtDistance.setText(String.format("%3.1f", distanceFromHome) + "m");
    }

    protected void updateAttitude() {
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        if (Math.round(attitude.getYaw()) < 0) {
            mActivity.mDroneYaw = 360 + Math.round(attitude.getYaw());
        }
        mActivity.txtYaw.setText((int) mActivity.mDroneYaw + "deg");

        //drawDroneMaker();
    }

    protected void updateAltitude() {
        Altitude altitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        mActivity.mDroneAltitude = altitude.getAltitude();
        mActivity.txtAltitude.setText(String.format("%3.1f", mActivity.mDroneAltitude) + "m");
    }

    protected void updateSpeed() {
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        mActivity.txtSpeed.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");
    }

    protected double distanceBetweenPoints(LatLongAlt pointA, LatLongAlt pointB) {
        if (pointA == null || pointB == null) {
            return 0;
        }
        double dx = pointA.getLatitude() - pointB.getLatitude();
        double dy = pointA.getLongitude() - pointB.getLongitude();
        double dz = pointA.getAltitude() - pointB.getAltitude();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

}
