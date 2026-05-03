import React, { memo, useEffect, useMemo } from 'react';
import { StyleSheet, View, Text, Platform } from 'react-native';
import MapView, { Marker, Polyline, Callout, PROVIDER_GOOGLE } from 'react-native-maps';
import Animated, { 
  useSharedValue, 
  useAnimatedStyle, 
  withRepeat, 
  withTiming, 
  withDelay,
  Easing
} from 'react-native-reanimated';

/**
 * PulseCircle - Animated ring for high severity markers
 */
const PulseCircle = ({ color }) => {
  const scale = useSharedValue(1);
  const opacity = useSharedValue(0.6);

  useEffect(() => {
    scale.value = withRepeat(
      withTiming(2.5, { duration: 2000, easing: Easing.out(Easing.ease) }),
      -1,
      false
    );
    opacity.value = withRepeat(
      withTiming(0, { duration: 2000, easing: Easing.out(Easing.ease) }),
      -1,
      false
    );
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
    opacity: opacity.value,
  }));

  return (
    <Animated.View 
      style={[
        styles.pulseRing, 
        { backgroundColor: color },
        animatedStyle
      ]} 
    />
  );
};

/**
 * NativeMap - react-native-maps based replacement for OSMMap.
 * 
 * Props:
 *   region    - { latitude, longitude, latitudeDelta, longitudeDelta }
 *   markers   - Array of { latitude, longitude, title, description, color, radius, time, severity }
 *   polylines - Array of { coordinates: [{latitude, longitude}], strokeColor, strokeWidth }
 *   onPress   - (event) => void (called with { latitude, longitude })
 *   showPositionMarker - boolean (if true, show a blue dot at the region center)
 *   style     - View style
 */
const NativeMap = ({ 
  region, 
  onPress, 
  markers = [], 
  polylines = [], 
  style, 
  showPositionMarker = false 
}) => {
  
  const handlePress = (e) => {
    if (onPress) {
      onPress({
        latitude: e.nativeEvent.coordinate.latitude,
        longitude: e.nativeEvent.coordinate.longitude,
      });
    }
  };

  // Convert severity to specific colors if not provided
  const getSeverityColor = (severity) => {
    switch (severity) {
      case 'high': return '#EF4444';
      case 'medium': return '#F59E0B';
      case 'low': return '#22C55E';
      case 'human': return '#3B82F6';
      default: return '#EF4444';
    }
  };

  return (
    <View style={[styles.container, style]}>
      <MapView
        provider={Platform.OS === 'android' ? PROVIDER_GOOGLE : undefined}
        style={styles.map}
        initialRegion={region}
        region={region}
        onPress={handlePress}
        customMapStyle={darkMapStyle}
        showsUserLocation={false}
        showsMyLocationButton={false}
        mapType="standard"
      >
        {polylines.map((polyline, index) => (
          <Polyline
            key={`poly-${index}`}
            coordinates={polyline.coordinates}
            strokeColor={polyline.strokeColor || '#3B82F6'}
            strokeWidth={polyline.strokeWidth || 4}
            lineJoin="round"
            lineCap="round"
          />
        ))}

        {markers.map((marker, index) => {
          const markerColor = marker.color || getSeverityColor(marker.severity);
          const radius = marker.radius || 8;
          
          return (
            <Marker
              key={`marker-${index}`}
              coordinate={{
                latitude: marker.latitude,
                longitude: marker.longitude,
              }}
              anchor={{ x: 0.5, y: 0.5 }}
              tracksViewChanges={false} // Optimization
            >
              <View style={[styles.markerContainer, { width: radius * 4, height: radius * 4 }]}>
                {marker.severity === 'high' && <PulseCircle color={markerColor} />}
                <View style={[
                  styles.dot, 
                  { 
                    width: radius * 1.5, 
                    height: radius * 1.5, 
                    borderRadius: radius * 0.75,
                    backgroundColor: markerColor,
                    borderColor: '#FFFFFF',
                    borderWidth: 2,
                  }
                ]} />
              </View>

              {(marker.title || marker.description || marker.time) && (
                <Callout tooltip>
                  <View style={styles.calloutContainer}>
                    <View style={styles.calloutContent}>
                      {marker.title && (
                        <Text style={[styles.calloutTitle, { color: markerColor }]}>
                          {marker.title}
                        </Text>
                      )}
                      {marker.time && (
                        <Text style={styles.calloutTime}>{marker.time}</Text>
                      )}
                      {marker.description && (
                        <Text style={styles.calloutDesc}>{marker.description}</Text>
                      )}
                      {marker.severity && (
                        <View style={[styles.severityBadge, styles[`severity-${marker.severity}`]]}>
                          <Text style={styles.severityText}>{marker.severity.toUpperCase()}</Text>
                        </View>
                      )}
                    </View>
                    <View style={styles.calloutTip} />
                  </View>
                </Callout>
              )}
            </Marker>
          );
        })}

        {showPositionMarker && region && (
          <Marker
            coordinate={{
              latitude: region.latitude,
              longitude: region.longitude,
            }}
            anchor={{ x: 0.5, y: 0.5 }}
          >
            <View style={styles.positionMarkerOuter}>
              <View style={styles.positionMarkerInner} />
            </View>
          </Marker>
        )}
      </MapView>
    </View>
  );
};

const darkMapStyle = [
  { "elementType": "geometry", "stylers": [{ "color": "#1E293B" }] },
  { "elementType": "labels.text.fill", "stylers": [{ "color": "#94A3B8" }] },
  { "elementType": "labels.text.stroke", "stylers": [{ "color": "#0B1326" }] },
  { "featureType": "administrative", "elementType": "geometry.stroke", "stylers": [{ "color": "#334155" }] },
  { "featureType": "landscape", "elementType": "geometry", "stylers": [{ "color": "#0B1326" }] },
  { "featureType": "poi", "elementType": "geometry", "stylers": [{ "color": "#1E293B" }] },
  { "featureType": "poi", "elementType": "labels.text.fill", "stylers": [{ "color": "#64748B" }] },
  { "featureType": "road", "elementType": "geometry", "stylers": [{ "color": "#131B2E" }] },
  { "featureType": "road", "elementType": "geometry.stroke", "stylers": [{ "color": "#1E293B" }] },
  { "featureType": "road.highway", "elementType": "geometry", "stylers": [{ "color": "#334155" }] },
  { "featureType": "water", "elementType": "geometry", "stylers": [{ "color": "#0F172A" }] }
];

const styles = StyleSheet.create({
  container: {
    flex: 1,
    overflow: 'hidden',
    backgroundColor: '#0B1326',
  },
  map: {
    flex: 1,
  },
  markerContainer: {
    justifyContent: 'center',
    alignItems: 'center',
  },
  pulseRing: {
    position: 'absolute',
    width: 20,
    height: 20,
    borderRadius: 10,
  },
  dot: {
    zIndex: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 2,
    elevation: 4,
  },
  calloutContainer: {
    width: 200,
    alignItems: 'center',
  },
  calloutContent: {
    backgroundColor: '#131B2E',
    borderRadius: 12,
    padding: 12,
    width: '100%',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.5,
    shadowRadius: 10,
    elevation: 8,
    borderWidth: 1,
    borderColor: '#1E293B',
  },
  calloutTip: {
    width: 0,
    height: 0,
    backgroundColor: 'transparent',
    borderStyle: 'solid',
    borderLeftWidth: 8,
    borderRightWidth: 8,
    borderTopWidth: 12,
    borderLeftColor: 'transparent',
    borderRightColor: 'transparent',
    borderTopColor: '#131B2E',
    marginTop: -1,
  },
  calloutTitle: {
    fontSize: 14,
    fontWeight: '800',
    marginBottom: 4,
  },
  calloutTime: {
    fontSize: 11,
    color: '#94A3B8',
    marginBottom: 4,
  },
  calloutDesc: {
    fontSize: 12,
    color: '#CBD5E1',
    lineHeight: 16,
    marginBottom: 6,
  },
  severityBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
  severityText: {
    fontSize: 9,
    fontWeight: '800',
    color: '#FFFFFF',
    letterSpacing: 0.5,
  },
  'severity-high': { backgroundColor: '#EF4444' },
  'severity-medium': { backgroundColor: '#F59E0B' },
  'severity-low': { backgroundColor: '#22C55E' },
  'severity-human': { backgroundColor: '#3B82F6' },
  
  positionMarkerOuter: {
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: '#FFFFFF',
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
    elevation: 3,
  },
  positionMarkerInner: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#007bff',
  },
});

export default memo(NativeMap);
