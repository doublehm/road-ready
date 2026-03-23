import React, { useRef, useEffect, memo } from 'react';
import { StyleSheet, View } from 'react-native';
import { WebView } from 'react-native-webview';

// ~10 metres in degrees - only pan the map if the user has moved this far
const MIN_MOVE_THRESHOLD = 0.0001;

/**
 * OSMMap - Leaflet/WebView based map that works on Android and iOS without
 * requiring a Google Maps API key.
 *
 * Props:
 *   region    - { latitude, longitude, latitudeDelta?, longitudeDelta? }
 *   markers   - [{ latitude, longitude, title?, description?, color? }]
 *   polylines - [{ coordinates: [{latitude, longitude}], strokeColor?, strokeWidth? }]
 *   style     - View style override
 *   onPress   - called with { latitude, longitude } on map tap
 */
const OSMMap = ({ region, onPress, markers = [], polylines = [], style }) => {
  const webViewRef = useRef(null);
  const lastSentRef = useRef(null);
  const initializedRef = useRef(false);

  const html = `<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <style>
    html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #1a1a2e; }
    .event-popup { font-size: 12px; line-height: 1.4; }
    .event-popup b { display: block; margin-bottom: 2px; }
  </style>
</head>
<body>
<div id="map"></div>
<script>
  var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([49.2827, -123.1207], 15);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);

  var posMarker = null;
  var drawnLines = [];
  var drawnMarkers = [];

  document.addEventListener('message', handle);
  window.addEventListener('message', handle);

  function handle(e) {
    try {
      var cmd = JSON.parse(e.data);
      if (cmd.type === 'setView') {
        map.setView([cmd.lat, cmd.lon], cmd.zoom || map.getZoom());
      } else if (cmd.type === 'fitBounds') {
        map.fitBounds([[cmd.south, cmd.west], [cmd.north, cmd.east]], { padding: [20, 20] });
      } else if (cmd.type === 'updateMarker') {
        if (!posMarker) {
          posMarker = L.circleMarker([cmd.lat, cmd.lon], {
            radius: 9, color: '#fff', weight: 2,
            fillColor: '#007bff', fillOpacity: 1
          }).addTo(map);
        } else {
          posMarker.setLatLng([cmd.lat, cmd.lon]);
        }
      } else if (cmd.type === 'updateMarkers') {
        drawnMarkers.forEach(function(m) { map.removeLayer(m); });
        drawnMarkers = [];
        (cmd.markers || []).forEach(function(m) {
          var cm = L.circleMarker([m.lat, m.lon], {
            radius: m.radius || 7,
            color: '#fff',
            weight: 1.5,
            fillColor: m.color || '#EF4444',
            fillOpacity: 0.9
          }).addTo(map);
          if (m.title || m.description) {
            var html = '<div class="event-popup">';
            if (m.title) html += '<b>' + m.title + '</b>';
            if (m.description) html += m.description;
            html += '</div>';
            cm.bindPopup(html);
          }
          drawnMarkers.push(cm);
        });
      } else if (cmd.type === 'updatePolylines') {
        drawnLines.forEach(function(l) { map.removeLayer(l); });
        drawnLines = [];
        cmd.lines.forEach(function(p) {
          if (p.coords && p.coords.length > 1) {
            drawnLines.push(L.polyline(p.coords, {
              color: p.color || '#007bff',
              weight: p.width || 4,
              opacity: 0.85
            }).addTo(map));
          }
        });
      } else if (cmd.type === 'init') {
        map.setView([cmd.lat, cmd.lon], 16);
      }
    } catch(err) {}
  }

  map.on('click', function(e) {
    var msg = JSON.stringify({ type: 'mapPress', lat: e.latlng.lat, lon: e.latlng.lng });
    if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(msg);
  });
</script>
</body>
</html>`;

  const send = (cmd) => {
    if (webViewRef.current) {
      webViewRef.current.postMessage(JSON.stringify(cmd));
    }
  };

  useEffect(() => {
    if (!region) return;
    const prev = lastSentRef.current;
    const moved = !prev ||
      Math.abs(region.latitude - prev.lat) > MIN_MOVE_THRESHOLD ||
      Math.abs(region.longitude - prev.lon) > MIN_MOVE_THRESHOLD;

    if (moved) {
      lastSentRef.current = { lat: region.latitude, lon: region.longitude };
      if (initializedRef.current) {
        send({ type: 'setView', lat: region.latitude, lon: region.longitude });
        send({ type: 'updateMarker', lat: region.latitude, lon: region.longitude });
      }
    }
  }, [region?.latitude, region?.longitude]);

  useEffect(() => {
    if (!initializedRef.current) return;
    send({
      type: 'updatePolylines',
      lines: polylines.map(p => ({
        coords: (p.coordinates || []).map(c => [c.latitude, c.longitude]),
        color: p.strokeColor || '#007bff',
        width: p.strokeWidth || 4,
      })),
    });
  }, [polylines]);

  const sendMarkers = () => {
    if (markers.length > 0) {
      send({
        type: 'updateMarkers',
        markers: markers.map(m => ({
          lat: m.latitude,
          lon: m.longitude,
          title: m.title || '',
          description: m.description || '',
          color: m.color || '#EF4444',
          radius: m.radius || 7,
        })),
      });
    }
  };

  useEffect(() => {
    if (!initializedRef.current) return;
    sendMarkers();
  }, [markers]);

  const onWebViewLoad = () => {
    initializedRef.current = true;
    if (region) {
      if (region.latitudeDelta && region.longitudeDelta) {
        send({
          type: 'fitBounds',
          south: region.latitude - region.latitudeDelta / 2,
          north: region.latitude + region.latitudeDelta / 2,
          west: region.longitude - region.longitudeDelta / 2,
          east: region.longitude + region.longitudeDelta / 2,
        });
      } else {
        send({ type: 'init', lat: region.latitude, lon: region.longitude });
      }
    }
    if (polylines.length > 0) {
      send({
        type: 'updatePolylines',
        lines: polylines.map(p => ({
          coords: (p.coordinates || []).map(c => [c.latitude, c.longitude]),
          color: p.strokeColor || '#007bff',
          width: p.strokeWidth || 4,
        })),
      });
    }
    sendMarkers();
  };

  const onWebViewMessage = (e) => {
    if (!onPress) return;
    try {
      const msg = JSON.parse(e.nativeEvent.data);
      if (msg.type === 'mapPress') {
        onPress({ latitude: msg.lat, longitude: msg.lon });
      }
    } catch (_) {}
  };

  return (
    <View style={[styles.container, style]}>
      <WebView
        ref={webViewRef}
        originWhitelist={['*']}
        source={{ html }}
        style={styles.map}
        onLoad={onWebViewLoad}
        onMessage={onWebViewMessage}
        javaScriptEnabled
        domStorageEnabled
        startInLoadingState={false}
        scrollEnabled={false}
        mixedContentMode="always"
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#1a1a2e',
  },
  map: {
    flex: 1,
    backgroundColor: 'transparent',
  },
});

export default memo(OSMMap);
