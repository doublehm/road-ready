package com.roadready.ui.components

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode

@Composable
actual fun AddressAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier
) {
    val context = LocalContext.current
    
    // Ensure Places is initialized
    LaunchedEffect(Unit) {
        if (!Places.isInitialized()) {
            val appInfo = context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
            val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")
            if (apiKey != null && apiKey != "YOUR_API_KEY") {
                Places.initialize(context, apiKey)
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val place = Autocomplete.getPlaceFromIntent(result.data!!)
            onValueChange(place.address ?: "")
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { }, // Read-only because we use the picker
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
                    val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                        .setCountry("CA") // Restrict to Canada based on project context
                        .build(context)
                    launcher.launch(intent)
                },
            enabled = false, // Disable manual typing to force the picker
            readOnly = true
        )
    }
}
