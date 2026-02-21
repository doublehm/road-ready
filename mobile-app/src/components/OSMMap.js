import React, { useRef, useEffect, memo } from 'react';
import { StyleSheet, View } from 'react-native';
import MapView, { Marker, Polyline, UrlTile } from 'react-native-maps';

// ~10 metres in degrees — only re-centre the map if user moved this far
const MIN_MOVE_THRESHOLD = 0.0001;

/**
 * Native OSM Map component using react-native-maps.
 * Provides a smooth, native experience without the overhead of a WebView.
 */
const OSMMap = ({
  region,
  onPress,
  markers = [],
  polylines = [],
  style
}) => {
  const mapRef = useRef(null);
  const lastAnimated = useRef(null);

  // Only animate to new region when the device has moved meaningfully.
  // Calling animateToRegion on every 1-second GPS tick (before the previous
  // animation finishes) is what causes the map to flicker/blink.
  useEffect(() => {
    if (!mapRef.current || !region) return;

    const prev = lastAnimated.current;
    if (prev) {
      const dLat = Math.abs(region.latitude - prev.latitude);
      const dLon = Math.abs(region.longitude - prev.longitude);
      if (dLat < MIN_MOVE_THRESHOLD && dLon < MIN_MOVE_THRESHOLD) return;
    }

    lastAnimated.current = { latitude: region.latitude, longitude: region.longitude };
    mapRef.current.animateToRegion({
      latitude: region.latitude,
      longitude: region.longitude,
      latitudeDelta: region.latitudeDelta || 0.01,
      longitudeDelta: region.longitudeDelta || 0.01,
    }, 400); // 400 ms — fast enough to stay in sync, short enough not to compete
  }, [region?.latitude, region?.longitude]);

  return (
    <View style={[styles.container, style]}>
      <MapView
        ref={mapRef}
        style={styles.map}
        initialRegion={{
          latitude: region?.latitude || 49.2827,
          longitude: region?.longitude || -123.1207,
          latitudeDelta: region?.latitudeDelta || 0.05,
          longitudeDelta: region?.longitudeDelta || 0.05,
        }}
        onPress={onPress}
        mapType="none" // Important: disable default tiles to show OSM
        rotateEnabled={false}
        pitchEnabled={false}
      >
        {/* OpenStreetMap Tile Overlay */}
        <UrlTile
          urlTemplate="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          maximumZ={19}
          flipY={false}
          shouldReplaceMapContent={true}
          zIndex={1}
        />

        {/* Render Markers */}
        {markers.map((m, idx) => (
          <Marker
            key={`marker-${idx}`}
            coordinate={{ latitude: m.latitude, longitude: m.longitude }}
            title={m.title}
            description={m.description}
          />
        ))}

        {/* Render Polylines */}
        {polylines.map((p, idx) => (
          <Polyline
            key={`poly-${idx}`}
            coordinates={p.coordinates}
            strokeWidth={p.strokeWidth || 4}
            strokeColor={p.strokeColor || '#007bff'}
          />
        ))}
      </MapView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#111',
  },
  map: {
    ...StyleSheet.absoluteFillObject,
  },
});

export default memo(OSMMap);
