import React, { useRef, useEffect } from 'react';
import { WebView } from 'react-native-webview';
import { StyleSheet, View, ActivityIndicator } from 'react-native';

const OSMMap = ({ 
  region, 
  onPress, 
  markers = [], 
  polylines = [], 
  style 
}) => {
  const webViewRef = useRef(null);

  const generateHTML = () => {
    return `
      <!DOCTYPE html>
      <html>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
          body { margin: 0; padding: 0; }
          #map { height: 100vh; width: 100vw; background: #111; }
          .leaflet-tile-pane {
             filter: brightness(0.6) invert(1) contrast(3) hue-rotate(200deg) saturate(0.3) brightness(0.7);
          }
        </style>
      </head>
      <body>
        <div id="map"></div>
        <script>
          var map = L.map('map', { zoomControl: false }).setView([${region.latitude}, ${region.longitude}], 15);
          
          L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; OpenStreetMap'
          }).addTo(map);

          var markersLayers = L.layerGroup().addTo(map);
          var polylinesLayers = L.layerGroup().addTo(map);

          function updateMarkers(newMarkers) {
            markersLayers.clearLayers();
            newMarkers.forEach((m) => {
              var marker = L.marker([m.latitude, m.longitude]);
              if (m.title || m.description) {
                marker.bindPopup('<b>' + (m.title || '') + '</b><br>' + (m.description || ''));
              }
              marker.addTo(markersLayers);
            });
          }

          function updatePolylines(newPolylines) {
             polylinesLayers.clearLayers();
             newPolylines.forEach((p) => {
               L.polyline(p.coordinates.map(c => [c.latitude, c.longitude]), {
                 color: p.strokeColor || '#007bff',
                 weight: p.strokeWidth || 4,
                 opacity: p.strokeOpacity || 0.8
               }).addTo(polylinesLayers);
             });
          }

          window.addEventListener('message', function(event) {
            var msg = JSON.parse(event.data);
            if (msg.type === 'updateRegion') {
              map.setView([msg.lat, msg.lng], map.getZoom());
            } else if (msg.type === 'updateData') {
              updateMarkers(msg.markers);
              updatePolylines(msg.polylines);
            }
          });

          // Initial load
          updateMarkers(${JSON.stringify(markers)});
          updatePolylines(${JSON.stringify(polylines)});
        </script>
      </body>
      </html>
    `;
  };

  useEffect(() => {
    if (webViewRef.current) {
      webViewRef.current.postMessage(JSON.stringify({
        type: 'updateRegion',
        lat: region.latitude,
        lng: region.longitude
      }));
      
      webViewRef.current.postMessage(JSON.stringify({
        type: 'updateData',
        markers,
        polylines
      }));
    }
  }, [region.latitude, region.longitude, markers, polylines]);

  return (
    <View style={[styles.container, style]}>
      <WebView
        ref={webViewRef}
        originWhitelist={['*']}
        source={{ html: generateHTML() }}
        style={styles.webview}
        scrollEnabled={false}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, overflow: 'hidden' },
  webview: { flex: 1 }
});

export default OSMMap;
