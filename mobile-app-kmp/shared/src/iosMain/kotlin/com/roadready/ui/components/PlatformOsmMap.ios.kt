package com.roadready.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.interop.UIKitView
import platform.MapKit.*
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.UIKit.*
import platform.Foundation.*
import platform.objc.*

@Composable
actual fun PlatformOsmMap(
    coordinates: List<Pair<Double, Double>>,
    events: List<RouteEvent>,
    height: Dp,
    modifier: Modifier,
    followCurrentLocation: Boolean,
) {
    val osmUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    
    Box(modifier = modifier.fillMaxSize()) {
        UIKitView(
            factory = {
                val mapView = MKMapView()
                mapView.setShowsUserLocation(true)
                
                // Add OSM Overlay
                val overlay = MKTileOverlay(URLTemplate = osmUrlTemplate)
                overlay.setCanReplaceMapContent(false) // Overlay on top of Apple Maps
                mapView.addOverlay(overlay, level = MKOverlayLevelAboveLabels)
                
                mapView.delegate = object : NSObject(), MKMapViewDelegateProtocol {
                    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer {
                        return if (rendererForOverlay is MKTileOverlay) {
                            MKTileOverlayRenderer(overlay = rendererForOverlay)
                        } else if (rendererForOverlay is MKPolyline) {
                            MKPolylineRenderer(polyline = rendererForOverlay).apply {
                                strokeColor = UIColor.systemBlueColor
                                lineWidth = 4.0
                            }
                        } else {
                            MKOverlayRenderer(overlay = rendererForOverlay)
                        }
                    }
                }
                mapView
            },
            modifier = Modifier.fillMaxSize(),
            update = { mapView ->
                // Update camera to follow location
                val lastCoord = coordinates.lastOrNull()
                if (followCurrentLocation && lastCoord != null) {
                    val center = CLLocationCoordinate2DMake(lastCoord.first, lastCoord.second)
                    val region = MKCoordinateRegionMakeWithDistance(center, 500.0, 500.0)
                    mapView.setRegion(region, animated = true)
                }
                
                // Draw Route Polyline
                if (coordinates.size > 1) {
                    // Logic to update polylines would go here in a full implementation
                }
            }
        )

        // OSM Attribution
        Text(
            text = "© OpenStreetMap contributors",
            fontSize = 10.sp,
            color = Color.Gray.copy(alpha = 0.8f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}
