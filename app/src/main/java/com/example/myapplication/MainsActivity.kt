package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMoveListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.search.autofill.AddressAutofill
import com.mapbox.search.autofill.AddressAutofillOptions
import com.mapbox.search.autofill.AddressAutofillResponse
import com.mapbox.search.autofill.AddressAutofillSuggestion
import com.mapbox.search.autofill.Query
import com.mapbox.search.ui.adapter.autofill.AddressAutofillUiAdapter
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.DistanceUnitType
import com.mapbox.search.ui.view.SearchResultsView

class MainsActivity : AppCompatActivity() {

    private lateinit var addressAutofill: AddressAutofill

    private lateinit var searchResultsView: SearchResultsView
    private lateinit var searchEngineUiAdapter: AddressAutofillUiAdapter

    private lateinit var queryEditText: EditText

    private lateinit var apartmentEditText: EditText
    private lateinit var cityEditText: EditText
    private lateinit var stateEditText: EditText
    private lateinit var zipEditText: EditText
    private lateinit var fullAddress: TextView
    private lateinit var pinCorrectionNote: TextView
    private lateinit var mapView: MapView
    private lateinit var mapPin: View
    private lateinit var mapboxMap: MapboxMap

    private var ignoreNextMapIdleEvent: Boolean = false
    private var ignoreNextQueryTextUpdate: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        addressAutofill = AddressAutofill.create(getString(R.string.mapbox_access_token))

        queryEditText = findViewById(R.id.query_text)
        apartmentEditText = findViewById(R.id.address_apartment)
        cityEditText = findViewById(R.id.address_city)
        stateEditText = findViewById(R.id.address_state)
        zipEditText = findViewById(R.id.address_zip)
        fullAddress = findViewById(R.id.full_address)
        pinCorrectionNote = findViewById(R.id.pin_correction_note)

        mapPin = findViewById(R.id.map_pin)
        mapPin.isVisible = true
        mapView = findViewById(R.id.map)
        mapboxMap = mapView.getMapboxMap()
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS) {
            mapView.location.updateSettings {
                enabled = true
                pulsingEnabled = true
            }
        }

        mapboxMap.addOnMoveListener(object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {

            }

            override fun onMoveEnd(detector: MoveGestureDetector) {
                if (ignoreNextMapIdleEvent) {
                    ignoreNextMapIdleEvent = false
                    return
                }

                val mapCenter = mapboxMap.cameraState.center
                findAddress(mapCenter)
            }

            override fun onMove(detector: MoveGestureDetector): Boolean {
                return false
            }
        })


        searchResultsView = findViewById(R.id.search_results_view)

        searchResultsView.initialize(
            SearchResultsView.Configuration(
                commonConfiguration = CommonSearchViewConfiguration(DistanceUnitType.IMPERIAL)
            )
        )

        searchEngineUiAdapter = AddressAutofillUiAdapter(
            view = searchResultsView,
            addressAutofill = addressAutofill
        )

        LocationEngineProvider.getBestLocationEngine(applicationContext).lastKnownLocationOrNull(this) { point ->
            point?.let {
                mapView.getMapboxMap().setCamera(
                    CameraOptions.Builder()
                        .center(point)
                        .zoom(15.0)
                        .build()
                )
                ignoreNextMapIdleEvent = true
            }
        }

        searchEngineUiAdapter.addSearchListener(object : AddressAutofillUiAdapter.SearchListener {

            override fun onSuggestionSelected(suggestion: AddressAutofillSuggestion) {
                showAddressAutofillSuggestion(
                    suggestion,
                    fromReverseGeocoding = false,
                )
            }

            override fun onSuggestionsShown(suggestions: List<AddressAutofillSuggestion>) {
                // Nothing to do
            }

            override fun onError(e: Exception) {
                // Nothing to do
            }
        })

        queryEditText.addTextChangedListener(object : TextWatcher {

            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                if (ignoreNextQueryTextUpdate) {
                    ignoreNextQueryTextUpdate = false
                    return
                }

                val query = Query.create(text.toString())


                val list = ArrayList<AddressAutofillOptions.Country>()
                list.add(AddressAutofillOptions.Country("ID"))

                if (query != null) {
                    lifecycleScope.launchWhenStarted {
                        searchEngineUiAdapter.search(query, AddressAutofillOptions(countries = list, language = AddressAutofillOptions.Language("id")))
                    }
                }
                searchResultsView.isVisible = query != null
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // Nothing to do
            }

            override fun afterTextChanged(s: Editable) {
                // Nothing to do
            }
        })

        if (!isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                PERMISSIONS_REQUEST_LOCATION
            )
        }
    }

    private fun findAddress(point: Point) {
        lifecycleScope.launchWhenStarted {
            when (val response = addressAutofill.suggestions(point, AddressAutofillOptions())) {
                is AddressAutofillResponse.Suggestions -> {
                    if (response.suggestions.isEmpty()) {
                        Toast.makeText(this@MainsActivity, "r", Toast.LENGTH_SHORT).show()
                    } else {
                        showAddressAutofillSuggestion(
                            response.suggestions.first(),
                            fromReverseGeocoding = true
                        )
                    }
                }
                is AddressAutofillResponse.Error -> {
                    val error = response.error
                    Log.d("Test.", "Test. $error", error)

                }
            }
        }
    }

    private fun showAddressAutofillSuggestion(suggestion: AddressAutofillSuggestion, fromReverseGeocoding: Boolean) {
        val address = suggestion.result().address
        cityEditText.setText(address.place)
        stateEditText.setText(address.region)
        zipEditText.setText(address.postcode)

        fullAddress.isVisible = true
        fullAddress.text = suggestion.formattedAddress

        pinCorrectionNote.isVisible = true

        if (!fromReverseGeocoding) {
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(suggestion.coordinate)
                    .zoom(16.0)
                    .build()
            )
            ignoreNextMapIdleEvent = true
            mapPin.isVisible = true
        }

        ignoreNextQueryTextUpdate = true
        queryEditText.setText(
            listOfNotNull(
                address.houseNumber,
                address.street
            ).joinToString()
        )
        queryEditText.clearFocus()

        searchResultsView.isVisible = false
        searchResultsView.hideKeyboard()
    }

    private fun showToast(@StringRes resId: Int) {
        Toast.makeText(applicationContext, getString(resId), Toast.LENGTH_SHORT).show()
    }

    private companion object {

        const val PERMISSIONS_REQUEST_LOCATION = 0

        fun Context.isPermissionGranted(permission: String): Boolean {
            return ContextCompat.checkSelfPermission(
                this, permission
            ) == PackageManager.PERMISSION_GRANTED
        }

        val Context.inputMethodManager: InputMethodManager
            get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        fun View.hideKeyboard() {
            context.inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
        }

        @SuppressLint("MissingPermission")
        fun LocationEngine.lastKnownLocationOrNull(context: Context, callback: (Point?) -> Unit) {
            if (!PermissionsManager.areLocationPermissionsGranted(context)) {
                callback(null)
            }

            val locationCallback = object : LocationEngineCallback<LocationEngineResult> {
                override fun onSuccess(result: LocationEngineResult?) {
                    val location = (result?.locations?.lastOrNull() ?: result?.lastLocation)?.let { location ->
                        Point.fromLngLat(location.longitude, location.latitude)
                    }
                    callback(location)
                }

                override fun onFailure(exception: Exception) {
                    callback(null)
                }
            }
            getLastLocation(locationCallback)
        }
    }
}
