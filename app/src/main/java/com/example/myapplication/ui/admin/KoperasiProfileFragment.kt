package com.example.myapplication.ui.admin

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.myapplication.data.audit.AuditLogger
import com.example.myapplication.data.auth.SessionManager
import com.example.myapplication.data.db.AppDatabase
import com.example.myapplication.data.db.SettingsEntity
import com.example.myapplication.databinding.FragmentKoperasiProfileBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class KoperasiProfileFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentKoperasiProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private var selectedQrisUri: Uri? = null
    private var currentQrisPath: String? = null

    private var googleMap: GoogleMap? = null
    private var marker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var selectedLatLng: LatLng? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedQrisUri = it
            binding.imgQrisPreview.load(it)
        }
    }

    private val requestLocationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            enableMyLocation()
        } else {
            Toast.makeText(requireContext(), "Izin lokasi ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentKoperasiProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = SessionManager(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager.findFragmentById(binding.mapContainer.id) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        loadData()
        
        binding.btnUploadQris.setOnClickListener { pickImage.launch("image/*") }
        binding.btnRemoveQris.setOnClickListener {
            selectedQrisUri = null
            currentQrisPath = null
            binding.imgQrisPreview.setImageResource(android.R.drawable.ic_menu_gallery)
        }
        binding.btnSaveProfile.setOnClickListener { saveData() }
        binding.btnCurrentLocation.setOnClickListener { getCurrentLocation() }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true
        
        googleMap?.setOnMapClickListener { latLng ->
            updateMarker(latLng)
        }

        enableMyLocation()
        
        // If data was already loaded before map was ready
        selectedLatLng?.let {
            updateMarker(it, moveCamera = true)
        }
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
        } else {
            requestLocationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val latLng = LatLng(it.latitude, it.longitude)
                updateMarker(latLng, moveCamera = true)
            } ?: run {
                Toast.makeText(requireContext(), "Tidak dapat mendeteksi lokasi saat ini", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMarker(latLng: LatLng, moveCamera: Boolean = false) {
        selectedLatLng = latLng
        binding.txtCoords.text = "Latitude: ${latLng.latitude}\nLongitude: ${latLng.longitude}"
        
        marker?.remove()
        marker = googleMap?.addMarker(MarkerOptions().position(latLng).title("Lokasi Koperasi"))
        
        if (moveCamera) {
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.get(requireContext())
            val settings = db.settingsDao().get() ?: SettingsEntity()
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.inputKoperasiName.setText(settings.koperasiName)
                binding.inputKoperasiAddress.setText(settings.koperasiAddress)
                currentQrisPath = settings.qrisImagePath
                if (currentQrisPath != null) {
                    binding.imgQrisPreview.load(File(currentQrisPath!!))
                }
                
                if (settings.latitude != null && settings.longitude != null) {
                    val latLng = LatLng(settings.latitude, settings.longitude)
                    selectedLatLng = latLng
                    updateMarker(latLng, moveCamera = true)
                }
            }
        }
    }

    private fun saveData() {
        val name = binding.inputKoperasiName.text.toString().trim()
        val address = binding.inputKoperasiAddress.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Nama koperasi wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                var finalPath = currentQrisPath
                selectedQrisUri?.let { uri ->
                    finalPath = saveImageToInternal(uri)
                }

                val db = AppDatabase.get(requireContext())
                val existing = db.settingsDao().get() ?: SettingsEntity()
                val newSettings = existing.copy(
                    koperasiName = name,
                    koperasiAddress = address,
                    qrisImagePath = finalPath,
                    latitude = selectedLatLng?.latitude,
                    longitude = selectedLatLng?.longitude,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                db.settingsDao().upsert(newSettings)
                
                AuditLogger.log(requireContext(), session.userId(), "UPDATE", "settings", 1, "Profile & Location updated")
                
                withContext(Dispatchers.Main) {
                    currentQrisPath = finalPath
                    selectedQrisUri = null
                    Toast.makeText(requireContext(), "Profil & Lokasi berhasil disimpan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImageToInternal(uri: Uri): String {
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val file = File(requireContext().filesDir, "qris_payment.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
