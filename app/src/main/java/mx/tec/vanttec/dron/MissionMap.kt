package mx.tec.vanttec.dron

import android.app.FragmentManager
import android.graphics.Color
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import dji.common.mission.waypoint.Waypoint
import dji.common.mission.waypoint.WaypointAction
import dji.common.mission.waypoint.WaypointActionType
import dji.common.model.LocationCoordinate2D
import io.reactivex.Observable

class MissionMap(private val fragmentManager: FragmentManager) : GoogleMap.OnMapClickListener,
        OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener {

    private val mMarkers = ArrayList<Marker>()
    private var gMap: GoogleMap? = null
    private var droneMarker: Marker? = null
    private lateinit var waypointListener: WayPointDialog.WayPointConfigListener
    private var waypoints = HashMap<LatLng, Waypoint>()
    private var geofenceOpts = PolygonOptions()
    private var geofence : Polygon? = null
    private var enableGeofence = false

    var addPinMode = AddPinMode.DISABLED

    val waypointObservable = Observable.create<Waypoint> {
        waypointListener =  object : WayPointDialog.WayPointConfigListener {
            override fun onWayPointConfigured(point: LatLng, height: Float, time: Int) {
                markWaypoint(point)
                val waypoint = Waypoint(point.latitude, point.longitude, height)
                waypoint.addAction(WaypointAction(WaypointActionType.STAY, time))

                waypoints[point] = waypoint

                it.onNext(waypoint)
            }

            override fun onWayPointCancelled() {
                // Do nothing
            }
        }
    }.publish()!!

    var exitCallback : () -> Unit = {}

    var lastDronePosition : LatLng? = null

    init {
        waypointObservable.connect()
    }

    private fun markWaypoint(point: LatLng) {
        //Create MarkerOptions object
        val markerOptions = MarkerOptions()
        markerOptions.position(point)
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        val marker = gMap?.addMarker(markerOptions)

        if(marker != null)
            mMarkers.add(marker)
    }

    fun clearWaypointMarkers() {
        for(marker in mMarkers)
            marker.remove()

        geofenceOpts = PolygonOptions()
        geofence?.remove()
        enableGeofence = false

        mMarkers.clear()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        if (gMap == null) {
            googleMap.setOnMapClickListener(this) // add the listener for click for a map object
            googleMap.setOnMarkerClickListener(this)

            gMap = googleMap
        }

        val tec = LatLng(25.65, -100.290943)
        gMap?.moveCamera(CameraUpdateFactory.newLatLng(tec))
        gMap?.mapType = GoogleMap.MAP_TYPE_HYBRID
    }

    override fun onMapClick(point: LatLng) {
        when (addPinMode) {
            // Open waypoint dialog
            AddPinMode.WAYPOINT -> {
                val waypointDialog = WayPointDialog.instantiate(point)

                waypointDialog.listener = waypointListener
                waypointDialog.show(fragmentManager, WAYPOINT_DIALOG_TAG)
            }

            AddPinMode.DISABLED -> { /* DO NOTHING */ }

            AddPinMode.GEOFENCE -> addGeofenceVertex(point)
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val waypoint = waypoints[marker.position]

        if (waypoint != null) {
            val waypointDialog = WayPointDialog.instantiate(waypoint)


            waypointDialog.missionMap = this
            waypointDialog.listener = object : WayPointDialog.WayPointConfigListener {
                override fun onWayPointConfigured(point: LatLng, height: Float, time: Int) {
                    waypoint.coordinate = LocationCoordinate2D(point.latitude, point.longitude)
                    waypoint.altitude = height
                    waypoint.getActionAtIndex(0).actionParam = time
                }

                override fun onWayPointCancelled() { /*Do nothing*/ }
            }

            waypointDialog.show(fragmentManager, WAYPOINT_DIALOG_TAG)

            return true
        }

        return false
    }

    // Drone state
    fun updateDronePosition(position: LatLng, heading: Float) {
        if(droneMarker == null) {
            val options = MarkerOptions()
            val icon = BitmapDescriptorFactory.fromResource(R.drawable.aircraft)

            options.position(position)
                .rotation(heading)
                .icon(icon)
                .anchor(0.5f,0.5f)

            droneMarker = gMap?.addMarker(options)
        } else {
            droneMarker?.position = position
            droneMarker?.rotation = heading
        }

        lastDronePosition = position

        val geofencePoints = geofence?.points

        if(geofencePoints != null) {
            if(enableGeofence) {
                if(geofencePoints.size > 2 && !PolyUtil.containsLocation(position, geofencePoints, false)) {
                    enableGeofence = false
                    geofence?.strokeColor = Color.RED
                    exitCallback()
                }
            } else {
                if(geofencePoints.size > 2 && PolyUtil.containsLocation(position, geofencePoints, false)) {
                    enableGeofence = true
                    geofence?.strokeColor = Color.GREEN
                }
            }
        }
    }

    fun moveCamera(cameraUpdate: CameraUpdate) {
        gMap?.moveCamera(cameraUpdate)
    }

    fun addGeofenceVertex(point: LatLng) {
        geofenceOpts.add(point)

        if(geofenceOpts.points.size > 3) {
            geofence?.remove()
            geofence = gMap?.addPolygon(geofenceOpts)
            geofence?.strokeColor = Color.RED
        }
    }

    enum class AddPinMode {
        DISABLED,
        WAYPOINT,
        GEOFENCE
    }

    companion object {
        private const val WAYPOINT_DIALOG_TAG = "waypoint_dialog"
    }
}