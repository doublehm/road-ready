import React from 'react';
import { render } from '@testing-library/react-native';
import OSMMap from '../OSMMap';

// --- Mock react-native-maps -------------------------------------------
// We capture a ref to the animateToRegion spy so each test can assert on it.
const mockAnimateToRegion = jest.fn();

jest.mock('react-native-maps', () => {
  const React = require('react');
  const { View } = require('react-native');

  const MockMapView = React.forwardRef(({ children }, ref) => {
    React.useImperativeHandle(ref, () => ({
      animateToRegion: mockAnimateToRegion,
    }));
    return <View testID="map-view">{children}</View>;
  });

  return {
    __esModule: true,
    default: MockMapView,
    Marker: ({ testID }) => <View testID={testID} />,
    Polyline: ({ testID }) => <View testID={testID} />,
    UrlTile: () => <View />,
  };
});
// ----------------------------------------------------------------------

const BASE = { latitude: 49.2827, longitude: -123.1207, latitudeDelta: 0.01, longitudeDelta: 0.01 };

// Tiny nudge — well under the 0.0001 threshold (~10 m)
const TINY_MOVE = { ...BASE, latitude: 49.28272, longitude: -123.12072 };

// Real move — well over the 0.0001 threshold
const BIG_MOVE = { ...BASE, latitude: 49.2837, longitude: -123.1197 };

describe('OSMMap — anti-flicker threshold', () => {
  beforeEach(() => mockAnimateToRegion.mockClear());

  it('animates on the very first render', () => {
    render(<OSMMap region={BASE} />);
    expect(mockAnimateToRegion).toHaveBeenCalledTimes(1);
    expect(mockAnimateToRegion).toHaveBeenCalledWith(
      expect.objectContaining({ latitude: BASE.latitude, longitude: BASE.longitude }),
      400,
    );
  });

  it('does NOT re-animate when GPS jitter is below threshold (~10 m)', () => {
    const { rerender } = render(<OSMMap region={BASE} />);
    mockAnimateToRegion.mockClear();

    rerender(<OSMMap region={TINY_MOVE} />);

    expect(mockAnimateToRegion).not.toHaveBeenCalled();
  });

  it('DOES re-animate when the device moves beyond threshold (~10 m)', () => {
    const { rerender } = render(<OSMMap region={BASE} />);
    mockAnimateToRegion.mockClear();

    rerender(<OSMMap region={BIG_MOVE} />);

    expect(mockAnimateToRegion).toHaveBeenCalledTimes(1);
    expect(mockAnimateToRegion).toHaveBeenCalledWith(
      expect.objectContaining({ latitude: BIG_MOVE.latitude, longitude: BIG_MOVE.longitude }),
      400,
    );
  });

  it('uses a 400 ms animation duration (not the old 1000 ms)', () => {
    render(<OSMMap region={BASE} />);
    const [[, duration]] = mockAnimateToRegion.mock.calls;
    expect(duration).toBe(400);
  });
});
