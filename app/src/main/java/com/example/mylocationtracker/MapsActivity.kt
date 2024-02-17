package com.example.mylocationtracker

import android.Manifest

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

import com.google.android.gms.location.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

import com.example.mylocationtracker.helper.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatter.ofPattern


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val REQUEST_LOCATION = 1 // request code to identify specific permission request
        private const val TAG = "MapsActivity" // for debugging
    }

    private lateinit var map: GoogleMap
    // private lateinit var binding: ActivityMapsBinding

    private val uiHelper = UiHelper()

    private lateinit var fusedLocClient: FusedLocationProviderClient
    // use it to request location updates and get the latest location

    // functionality to continuously request location
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var database: FirebaseDatabase
    private lateinit var pathIdString: String

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // binding = ActivityMapsBinding.inflate(layoutInflater)
        // setContentView(binding.root)

        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val current = LocalDateTime.now()
        val formatter = ofPattern("yyyyMMddHHmmssSSS")

        // Get the device identifier for exporting to database
        // val deviceId = Build.ID
        val deviceID = Settings.Secure.getString(contentResolver,Settings.Secure.ANDROID_ID)

        // initialize this values from a broader scope
        database  = FirebaseDatabase.getInstance()
        pathIdString = deviceID + "-" + current.format(formatter)

        // added creation of location callback
        createLocationCallback()

        setupLocClient()
        locationRequest = uiHelper.getLocationRequest()

        // continuously update location (does it?)
        requestLocationUpdate()

        // get reference to button
        val buttonClickMe = findViewById(R.id.btn_store_location) as Button
        // set on-click listener

        // set the initial numeric value to identify stored locations
        // var station = 1

        buttonClickMe.setOnClickListener {
            // your code to perform when the user clicks on the button
            // val padStepString = station.toString().padStart(4, '0')
            // val stepId = "$pathIdString/stations/$padStepString"

            val currentStTime = LocalDateTime.now()
            val formatter = ofPattern("yyyyMMddHHmmssSSS")
            val currentTimeString = currentStTime.format(formatter)

            val currentStationString = "$pathIdString/stations/$currentTimeString"

            getAndStoreCurrentLocation(storeIt = true, storeId = currentStationString)
            // increment station counter for this path
            // station+=1
            Toast.makeText(this@MapsActivity, "Se guardó la ubicación", Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // Add a marker in Sydney and move the camera
        // val sydney = LatLng(-34.0, 151.0)
        // mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        // mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))

        // get location only, do not pass arguments
        getAndStoreCurrentLocation()
        // requestLocationUpdate()

    }

    private fun setupLocClient() {
        fusedLocClient =
            LocationServices.getFusedLocationProviderClient(this)
    }

    // prompt the user to grant/deny access
    private fun requestLocPermissions() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), //permission in the manifest
            REQUEST_LOCATION)
    }

    private fun getAndStoreCurrentLocation(storeIt: Boolean=false, storeId: String="") {
        // Check if the ACCESS_FINE_LOCATION permission was granted before requesting a location
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            // If the permission has not been granted, then requestLocationPermissions() is called.
            requestLocPermissions()
        } else {

            fusedLocClient.lastLocation.addOnCompleteListener {
                // lastLocation is a task running in the background
                val location = it.result //obtain location

                if (location != null) {

                    val latLng = LatLng(location.latitude, location.longitude)
                    // create an object that will specify how the camera will be updated
                    val update = CameraUpdateFactory.newLatLngZoom(latLng, 19.0f)

                    map.moveCamera(update)
                    // Save the location data to the database
                    // store location or not, according to the passed argument
                    if (storeIt) {
                        //Get a reference to the database, so your app can perform read and write operations
                        val ref: DatabaseReference = database.getReference(storeId)
                        ref.setValue(location)

                        // create a marker at the exact location
                        // map.addMarker(MarkerOptions().position(latLng).title("You are currently here!"))
                        map.addMarker(MarkerOptions().position(latLng))
                    }

                } else {
                    // if location is null , log an error message
                    Log.e(TAG, "No location found")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdate() {
/*
        if (!uiHelper.isHaveLocationPermission(this)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
            return
        }
        if (uiHelper.isLocationProviderEnabled(this))
            uiHelper.showPositiveDialogWithListener(this, resources.getString(R.string.need_location), resources.getString(R.string.location_content), object : IPositiveNegativeListener {
                override fun onPositive() {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }, "Turn On", false)
*/
        fusedLocClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
    }


    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                if (locationResult!!.lastLocation == null) return
                // val latLng = LatLng(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)
                // Log.e("Location", latLng.latitude.toString() + " , " + latLng.longitude)

                val current = LocalDateTime.now()
                val formatter = ofPattern("yyyyMMddHHmmssSSS")
                val currentTimeString = current.format(formatter)

                val currentTrackString = "$pathIdString/tracks/$currentTimeString"

                val ref: DatabaseReference = database.getReference(currentTrackString)
                ref.setValue(locationResult.lastLocation)


/*
                if (locationFlag) {
                    locationFlag = false
                    animateCamera(latLng)
                }
                if (driverOnlineFlag) firebaseHelper.updateDriver(Driver(lat = latLng.latitude, lng = latLng.longitude))
                showOrAnimateMarker(latLng)
*/
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        //check if the request code matches the REQUEST_LOCATION
        if (requestCode == REQUEST_LOCATION)
        {
            //check if grantResults contains PERMISSION_GRANTED.If it does, call getCurrentLocation()
            if (grantResults.size == 1 && grantResults[0] ==
                PackageManager.PERMISSION_GRANTED) {
                // get location only, do not pass arguments
                // getAndStoreCurrentLocation()
                requestLocationUpdate()
            } else {
                //if it does not log an error message
                Log.e(TAG, "Location permission denied")
            }
        }
    }


}