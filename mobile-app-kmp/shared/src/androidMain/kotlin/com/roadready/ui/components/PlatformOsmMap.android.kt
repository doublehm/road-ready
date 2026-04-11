package com.roadready.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformOsmMap(
    coordinates: List<Pair<Double, Double>>,
    events: List<RouteEvent>,
    height: Dp,
    modifier: Modifier,
) {
    val html = remember(coordinates, events) { buildLeafletHtml(coordinates, events) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.parseColor("#0B1326"))
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier.fillMaxWidth().height(height),
    )
}

private fun buildLeafletHtml(
    coordinates: List<Pair<Double, Double>>,
    events: List<RouteEvent>,
): String {
    val centerLat = coordinates.map { it.first }.average().takeIf { !it.isNaN() } ?: 45.4215
    val centerLng = coordinates.map { it.second }.average().takeIf { !it.isNaN() } ?: -75.6972
    val zoom = if (coordinates.size > 1) 14 else 13

    val coordsJs = coordinates.joinToString(",") { "[${it.first},${it.second}]" }

    val eventsJs = events.joinToString(",") { e ->
        val color = when (e.severity) {
            "high" -> "#EF4444"
            "medium" -> "#F59E0B"
            else -> "#3B82F6"
        }
        """{"lat":${e.lat},"lng":${e.lng},"type":"${e.type}","color":"$color","desc":"${e.description.replace("\"", "'")}"}"""
    }

    return """
<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  body{margin:0;padding:0;background:#0B1326}
  #map{width:100%;height:100vh}
  .leaflet-control-attribution{font-size:8px!important}
</style>
</head><body>
<div id="map"></div>
<script>
var map=L.map('map',{zoomControl:false}).setView([$centerLat,$centerLng],$zoom);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
  attribution:'© OSM',maxZoom:19
}).addTo(map);

var coords=[$coordsJs];
if(coords.length>1){
  var route=L.polyline(coords,{color:'#3B82F6',weight:4,opacity:0.8}).addTo(map);
  map.fitBounds(route.getBounds().pad(0.1));
  L.circleMarker(coords[0],{radius:8,color:'#15803D',fillColor:'#15803D',fillOpacity:1}).addTo(map).bindPopup('Start');
  L.circleMarker(coords[coords.length-1],{radius:8,color:'#EF4444',fillColor:'#EF4444',fillOpacity:1}).addTo(map).bindPopup('End');
}

var events=[$eventsJs];
events.forEach(function(e){
  L.circleMarker([e.lat,e.lng],{radius:6,color:e.color,fillColor:e.color,fillOpacity:0.8})
    .addTo(map).bindPopup(e.type.replace('_',' ')+': '+e.desc);
});
</script>
</body></html>
""".trimIndent()
}
