export const FAULT_CATEGORIES = [
  {
    id: 'A',
    title: 'Observation',
    color: '#ffebee', // Light Red
    items: [
      { code: 'A1', label: 'Shoulder Check' },
      { code: 'A2', label: 'Scan' },
      { code: 'A3', label: 'Mirror Check' },
      { code: 'A4', label: '360° Check' },
      { code: 'A5', label: 'Direction of Travel' },
      { code: 'A6', label: 'Backing' },
      { code: 'A7', label: 'Hazard Perception' },
      { code: 'A8', label: 'Other' },
    ]
  },
  {
    id: 'B',
    title: 'Space Margins',
    color: '#e3f2fd', // Light Blue
    items: [
      { code: 'B1', label: 'Lane Position' },
      { code: 'B2', label: 'Follow Distance' },
      { code: 'B3', label: 'Stops Too Close/Far' },
      { code: 'B4', label: 'Gap' },
      { code: 'B5', label: 'Blocks Crosswalk' },
      { code: 'B6', label: 'Turn Position' },
      { code: 'B7', label: 'Occupied Crosswalk' },
      { code: 'B8', label: 'Manoeuvre Location' },
      { code: 'B9', label: 'Other' },
      { code: 'B10', label: 'Stop Position' },
      { code: 'B11', label: 'Road Position (Parking Lot)' },
      { code: 'B12', label: '3-Point/U-Turn' },
      { code: 'B13', label: 'Parking Margins' },
      { code: 'B14', label: 'Railroad Crossing' },
    ]
  },
  {
    id: 'C',
    title: 'Speed',
    color: '#e8f5e9', // Light Green
    items: [
      { code: 'C1', label: 'Speed Maintenance' },
      { code: 'C2', label: 'Rolling Stop' },
      { code: 'C3', label: 'Amber Light' },
      { code: 'C4', label: 'Accel/Decel' },
      { code: 'C5', label: 'Shifting' },
      { code: 'C6', label: 'Rolling Back' },
      { code: 'C7', label: 'Other' },
      { code: 'C8', label: 'Covers Brakes' },
      { code: 'C9', label: 'Parking Brake' },
    ]
  },
  {
    id: 'D',
    title: 'Steering',
    color: '#fff3cd', // Light Yellow
    items: [
      { code: 'D1', label: 'General Steering' },
      { code: 'D2', label: 'Other' },
      { code: 'D3', label: 'Steering Wheel Position' },
      { code: 'D4', label: 'Weight Transfer' },
    ]
  },
  {
    id: 'E',
    title: 'Communication',
    color: '#f3e5f5', // Light Purple
    items: [
      { code: 'E1', label: 'Signal' },
      { code: 'E2', label: 'Timing' },
      { code: 'E3', label: 'Cancel' },
      { code: 'E4', label: 'Other' },
    ]
  },
  {
    id: 'F',
    title: 'Device Detected',
    color: '#e0e0e0', // Light Grey
    items: [
      { code: 'F1', label: 'Harsh Braking' },
      { code: 'F2', label: 'Speeding' },
      { code: 'F3', label: 'Sharp Turn' },
      { code: 'F4', label: 'Sudden Stop' },
    ]
  }
];

// Flat array of all criteria with category metadata (for search/autocomplete)
export const ALL_CRITERIA = FAULT_CATEGORIES.flatMap(cat =>
  cat.items.map(item => ({
    ...item,
    categoryId: cat.id,
    categoryTitle: cat.title,
    categoryColor: cat.color,
  }))
);

// Map device event types to F-codes for unified tracking
export const DEVICE_EVENT_TO_CODE = {
  harsh_braking: { code: 'F1', label: 'Harsh Braking', category: 'F' },
  speeding: { code: 'F2', label: 'Speeding', category: 'F' },
  sharp_turn: { code: 'F3', label: 'Sharp Turn', category: 'F' },
  sudden_stop: { code: 'F4', label: 'Sudden Stop', category: 'F' },
};
