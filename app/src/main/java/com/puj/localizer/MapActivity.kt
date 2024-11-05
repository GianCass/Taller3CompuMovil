package com.puj.localizer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.puj.localizer.databinding.ActivityMapaBinding
import com.puj.localizer.R
import com.puj.localizer.databinding.ActivityMainBinding

class MapActivity: AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var auth: FirebaseAuth = Firebase.auth

    val database = FirebaseDatabase.getInstance()
    val userRef = database.getReference("usuarios")
    protected var currentUser = auth.currentUser

    lateinit var binding: ActivityMapaBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMapaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar el cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configurar el fragmento del mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Configurar las actualizaciones de ubicación
        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (::mMap.isInitialized) { // Verificar si mMap ya está inicializado
                    if(binding.trackSwitch.isChecked){
                        for (location in locationResult.locations) {
                            val currentCoords = mapOf(
                                "Lat" to location.latitude,
                                "Long" to location.longitude
                            )

                            userRef.child(currentUser!!.uid).child("Posicion").setValue(currentCoords)
                                .addOnFailureListener{ error ->
                                    Log.e("Firebase", "Error agregando las coordenadas del usuario", error)
                                }
                        }
                    }
                }
            }
        }

        binding.buttonProfile.setOnClickListener{

            val intentLog = Intent(this, ProfileActivity::class.java)
            startActivity(intentLog)
        }
        binding.buttonLogout.setOnClickListener{
            logout()
      }

        //Actualizar marcadores
        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                loadMarkers(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Database error: ${error.message}")
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        mMap.isMyLocationEnabled = true

        mMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            location?.let {
                val userLatLng = LatLng(it.latitude, it.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLng(userLatLng))
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f))
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if(auth.currentUser == null){
            logout()
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (::mMap.isInitialized) { // Verificar que mMap esté inicializado
                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    //Marcadores
    private fun loadMarkers(snapshot: DataSnapshot){
        mMap.clear()

        for(userSnapshot in snapshot.children){
            val nombre = userSnapshot.child("Nombre").getValue(String::class.java)!!
            val lat = userSnapshot.child("Posicion").child("Lat").getValue(Double::class.java)!!
            val long = userSnapshot.child("Posicion").child("Long").getValue(Double::class.java)!!
            val userId = userSnapshot.key
            var icon: BitmapDescriptor?

            val iconDrawable = ContextCompat.getDrawable(this, R.drawable.default_avatar) as BitmapDrawable
            val iconBitmap = iconDrawable.bitmap

            icon = BitmapDescriptorFactory.fromBitmap(iconBitmap)
            icon = BitmapDescriptorFactory.fromBitmap(
                ResizeMarkerIcon(this, iconBitmap, 64, 64)
            )

            val path = "profile_images/${userId}.jpg"
            val storageReference = FirebaseStorage.getInstance().reference.child(path)

            storageReference.downloadUrl.addOnSuccessListener { uri ->
                loadImageFromUri(uri) { bitmap ->
                    if (bitmap != null) {
                        icon = BitmapDescriptorFactory.fromBitmap(
                            ResizeMarkerIcon(this, bitmap, 64, 64)
                        )
                    } else {
                        Log.e("Firebase", "Failed to load the image bitmap")
                    }
                }
            }.addOnFailureListener{ exception ->
                Log.e("Firebase", "Error con la foto de perfil", exception)
            }

            if(userId != currentUser?.uid){
                val coords = LatLng(lat, long)
                if (::mMap.isInitialized) {
                    mMap.addMarker(
                        MarkerOptions()
                            .position(coords)
                            .title(nombre)
                            .icon((icon))
                        )
                }
            }
        }
    }

    private fun ResizeMarkerIcon(context: Context, drawableId: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(drawableId, width, height, false)
    }

    //Cerrar sesion
    protected fun logout(){
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }


    private fun loadImageFromUri(uri: Uri, callback: (Bitmap?) -> Unit) {
        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                callback(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null)
            }
        }.start()
    }
}