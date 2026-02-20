/**
 * Road Ready Map Helper
 * Reusable Leaflet components with Dark Theme support
 */

const RoadReadyMaps = {
    // Tile layer configurations
    tiles: {
        light: {
            url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
            attribution: '&copy; OpenStreetMap contributors'
        },
        dark: {
            url: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
            attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
            subdomains: 'abcd',
            maxZoom: 20
        }
    },

    // Initialize a map with default settings
    init: function(elementId, coords, zoom = 13) {
        const isDarkMode = document.documentElement.getAttribute('data-bs-theme') === 'dark';
        const tileConfig = isDarkMode ? this.tiles.dark : this.tiles.light;

        const map = L.map(elementId).setView(coords, zoom);
        L.tileLayer(tileConfig.url, {
            attribution: tileConfig.attribution,
            subdomains: tileConfig.subdomains || '',
            maxZoom: tileConfig.maxZoom || 19
        }).addTo(map);

        return map;
    },

    // Custom Icons
    icons: {
        pickup: new L.Icon({
            iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-green.png',
            shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
            iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34], shadowSize: [41, 41]
        }),
        dropoff: new L.Icon({
            iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-red.png',
            shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
            iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34], shadowSize: [41, 41]
        }),
        start: new L.Icon({
            iconUrl: 'https://raw.githubusercontent.com/pointhi/leaflet-color-markers/master/img/marker-icon-2x-blue.png',
            shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/0.7.7/images/marker-shadow.png',
            iconSize: [25, 41], iconAnchor: [12, 41], popupAnchor: [1, -34], shadowSize: [41, 41]
        })
    }
};
