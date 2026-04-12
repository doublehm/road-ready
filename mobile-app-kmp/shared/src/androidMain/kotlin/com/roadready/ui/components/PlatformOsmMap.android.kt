package com.roadready.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
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
    // Throttle: only update map HTML every 5 seconds or when events change
    val throttledCoords = remember { mutableStateOf(coordinates) }
    val lastUpdate = remember { mutableLongStateOf(0L) }

    LaunchedEffect(coordinates.size, events.size) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate.longValue > 5000 || coordinates.size <= 2) {
            throttledCoords.value = coordinates
            lastUpdate.longValue = now
        }
    }

    val html = remember(throttledCoords.value, events) {
        buildLeafletHtml(throttledCoords.value, events)
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowContentAccess = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                webViewClient = WebViewClient()
                setBackgroundColor(android.graphics.Color.parseColor("#0B1326"))
                loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            // Use JS to update route without reloading entire page
            val coordsJs = throttledCoords.value.joinToString(",") { "[${it.first},${it.second}]" }
            webView.evaluateJavascript("if(typeof updateRoute==='function')updateRoute([$coordsJs]);", null)
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
    val zoom = if (coordinates.size > 1) 15 else 14

    val coordsJs = coordinates.joinToString(",") { "[${it.first},${it.second}]" }

    val eventsJs = events.filter { it.lat != 0.0 && it.lng != 0.0 }.joinToString(",") { e ->
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

var route=null;
var startMarker=null;
var posMarker=null;

function updateRoute(coords){
  if(!coords||coords.length===0)return;
  if(route)map.removeLayer(route);
  if(startMarker)map.removeLayer(startMarker);
  if(posMarker)map.removeLayer(posMarker);
  if(coords.length>1){
    route=L.polyline(coords,{color:'#3B82F6',weight:4,opacity:0.8}).addTo(map);
    startMarker=L.circleMarker(coords[0],{radius:8,color:'#15803D',fillColor:'#15803D',fillOpacity:1}).addTo(map);
  }
  posMarker=L.circleMarker(coords[coords.length-1],{radius:8,color:'#3B82F6',fillColor:'#3B82F6',fillOpacity:1}).addTo(map);
  map.setView(coords[coords.length-1],map.getZoom());
}

var coords=[$coordsJs];
if(coords.length>0)updateRoute(coords);

var events=[$eventsJs];
events.forEach(function(e){
  if(e.lat===0&&e.lng===0)return;
  L.circleMarker([e.lat,e.lng],{radius:6,color:e.color,fillColor:e.color,fillOpacity:0.8})
    .addTo(map).bindPopup(e.type.replace('_',' ')+': '+e.desc);
});
</script>
</body></html>
""".trimIndent()
}
