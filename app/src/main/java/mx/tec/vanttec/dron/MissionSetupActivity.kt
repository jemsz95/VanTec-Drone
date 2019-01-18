package mx.tec.vanttec.dron

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowManager
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import dji.common.camera.SettingsDefinitions
import dji.common.error.DJIError
import dji.common.mission.waypoint.*
import dji.sdk.camera.MediaFile
import dji.sdk.camera.MediaManager
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager
import dji.sdk.mission.waypoint.WaypointMissionOperator
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener
import dji.sdk.products.Aircraft
import dji.sdk.sdkmanager.DJISDKManager
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_mission_setup.*

class MissionSetupActivity : AppCompatActivity() {
    private val missionMap = MissionMap(fragmentManager)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    arrayOf(
                            Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.READ_PHONE_STATE
                    ),
                    1
            )
        }

        val sdkManagerCallback = SDKManagerCallback()

        sdkManagerCallback.sdkObservable.subscribe { sdk ->
            var missionBuilder = WaypointMission.Builder()

            runOnUiThread {
                setContentView(R.layout.activity_mission_setup)

                initUI()

                // Map async
                val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment

                mapFragment.getMapAsync(missionMap)

                missionMap.waypointObservable.subscribe {
                    it.addAction(WaypointAction(WaypointActionType.START_TAKE_PHOTO,0))
                    missionBuilder.addWaypoint(it)
                }

                clearWaypoints.setOnClickListener {
                    missionMap.clearWaypointMarkers()
                    missionBuilder = WaypointMission.Builder()
                }
            }

            sdkManagerCallback.productObservable.subscribe { product ->
                if(product is Aircraft) {
                    product.flightController.setStateCallback { state ->
                        runOnUiThread {
                            val pos = LatLng(state.aircraftLocation.latitude, state.aircraftLocation.longitude)
                            val head = product.flightController.compass.heading
                            missionMap.updateDronePosition(pos, head)
                        }
                    }

                    val operator = sdk.missionControl.waypointMissionOperator

                    launch.setOnClickListener {
                        configWayPointMission(operator, product, missionBuilder)
                    }

                    missionMap.exitCallback = {
                        product.flightController.startGoHome { }
                    }

                    val videoFeedObservable = Observable.create<LiveFeedDecoder.LiveFeedData> {
                        VideoFeeder.getInstance().primaryVideoFeed.setCallback { data, length ->
                            it.onNext(LiveFeedDecoder.LiveFeedData(data, length))
                        }
                    }.publish().autoConnect()

                    val decoder = DJICodecManager(this@MissionSetupActivity,
                            liveFeed.holder,
                            liveFeed.width,
                            liveFeed.height)

                    videoFeedObservable.subscribe { (data, len) ->
                        decoder.sendDataToDecoder(data, len)
                    }



                    // val decoder = LiveFeedDecoder(this, videoFeedObservable)
                }
            }
        }

        DJISDKManager.getInstance().registerApp(this, sdkManagerCallback)

        // Hide keyboard
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        // Prevent screen from locking
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /*********************
     * Activity init functions
     */

    // Init view layout variables
    private fun initUI() {
        locate.setOnClickListener { centerCameraOnLocation() }
        addPin.setOnClickListener { cycleAddPin() }
        settings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun cycleAddPin() {
        missionMap.addPinMode = when(missionMap.addPinMode) {
            MissionMap.AddPinMode.DISABLED -> MissionMap.AddPinMode.GEOFENCE
            MissionMap.AddPinMode.GEOFENCE -> MissionMap.AddPinMode.WAYPOINT
            MissionMap.AddPinMode.WAYPOINT -> MissionMap.AddPinMode.DISABLED
        }

        val color = when(missionMap.addPinMode) {
            MissionMap.AddPinMode.WAYPOINT -> resources.getColor(R.color.colorPrimary, theme)
            MissionMap.AddPinMode.GEOFENCE -> resources.getColor(R.color.colorAccent, theme)
            MissionMap.AddPinMode.DISABLED -> resources.getColor(R.color.white, theme)
        }

        addPin.setColorFilter(color)
    }

    private fun centerCameraOnLocation() {
        val permission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)

        if(permission == PackageManager.PERMISSION_GRANTED) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location?) {
                    Log.d(TAG, "Location changed!")
                    if(location != null) {
                        val lat = location.latitude
                        val lng = location.longitude
                        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 18f)

                        missionMap.moveCamera(cameraUpdate)
                        locationManager.removeUpdates(this)
                    }
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                override fun onProviderEnabled(provider: String?) {}

                override fun onProviderDisabled(provider: String?) {}
            }

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    1f,
                    listener)

            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    LatLng(lastLocation.latitude, lastLocation.longitude),
                    17f)

            missionMap.moveCamera(cameraUpdate)
        }
    }

    private fun switchCameraMode(product : Aircraft, cameraMode: SettingsDefinitions.CameraMode) {
        product.camera?.setMode(cameraMode) { error ->
            if (error == null) {
                setResultToToast("Switch Camera Mode Succeeded")
            } else {
                setResultToToast(error.description)
            }
        }
    }

    private fun configWayPointMission(waypointMissionOperator: WaypointMissionOperator,
                                      product: Aircraft,
                                      missionBuilder: WaypointMission.Builder) {
        val mSpeed = 3.0f
        missionBuilder.finishedAction(WaypointMissionFinishedAction.NO_ACTION)
                .headingMode(WaypointMissionHeadingMode.AUTO)
                .autoFlightSpeed(mSpeed)
                .maxFlightSpeed(mSpeed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL)

        product.flightController.setGoHomeHeightInMeters(30) {
            val error = waypointMissionOperator.loadMission(missionBuilder.build())
            if (error == null) {
                setResultToToast("loadWaypoint succeeded")
                uploadWayPointMission(waypointMissionOperator, product, missionBuilder.waypointCount)
            } else {
                setResultToToast("loadWaypoint failed " + error.description)
            }
        }
    }

    private fun uploadWayPointMission(waypointMissionOperator: WaypointMissionOperator,
                                      product: Aircraft,
                                      count: Int) {

        waypointMissionOperator.uploadMission { error ->
            if (error == null) {
                waypointMissionOperator.addListener(object : WaypointMissionOperatorListener {
                    override fun onExecutionFinish(p0: DJIError?) {
                        switchCameraMode(product, SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD)
                        product.camera.mediaManager?.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD) {
                            if(it != null) {
                                Log.e(TAG, it.description)
                            } else {
                                val files = product.camera.mediaManager?.internalStorageFileListSnapshot ?: emptyList()

                                val latest = files.sortedBy{ it.timeCreated }
                                        .takeLast(count)

                                for ((i, file) in latest.withIndex()) {
                                    //file.
                                }
                            }
                        }

                    }

                    override fun onExecutionStart() {
                        //Blank
                    }

                    override fun onUploadUpdate(event: WaypointMissionUploadEvent) {
                        if(event.currentState == WaypointMissionState.READY_TO_EXECUTE)
                            startWaypointMission(waypointMissionOperator, product)
                    }

                    override fun onDownloadUpdate(p0: WaypointMissionDownloadEvent) {
                        //Blank
                    }

                    override fun onExecutionUpdate(p0: WaypointMissionExecutionEvent) {
                        //Blank
                    }

                })
            } else {
                setResultToToast("Mission upload failed, error: " + error.description + " retrying...")
            }
        }
    }

    private fun startWaypointMission(waypointMissionOperator: WaypointMissionOperator, product: Aircraft) {
        switchCameraMode(product, SettingsDefinitions.CameraMode.SHOOT_PHOTO)
        val camera = product.cameras.first()

        val logErr : (DJIError?) -> Unit = { if(it != null) Log.e(TAG, it.toString()) }

        camera.setPhotoAspectRatio(SettingsDefinitions.PhotoAspectRatio.RATIO_16_9, logErr)
        camera.setFocusMode(SettingsDefinitions.FocusMode.AUTO, logErr)
        camera.setExposureMode(SettingsDefinitions.ExposureMode.PROGRAM, logErr)

        waypointMissionOperator.startMission { error ->
            val status = if (error == null) "Success" else error.description
            setResultToToast("Mission Start: $status")
        }
    }

    private fun setResultToToast(string: String) {
        runOnUiThread { Toast.makeText(this, string, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        private const val TAG = "Main Activity"
    }
}
