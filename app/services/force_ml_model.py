"""
Force detection enhancement using three statistical modules:

1. ForceDecorrelator — Gram-Schmidt partial regression to orthogonalize
   the three vehicle force axes (lat, lon, vert).  Removes spurious
   covariance introduced by imperfect phone orientation and sensor
   cross-talk before any detection threshold is applied.

2. ForceRidgeAnalyzer — Ridge regression on decorrelated per-ride features.
   Computes inter-metric correlation matrix, ranks feature importances, and
   calibrates per-ride detection thresholds via importance-weighted scaling.

3. ForceESN — Echo State Network (random-projection reservoir + Ridge readout).
   Captures temporal sequences of force events without autograd or heavy
   ML libraries.  Trained per-ride using physics detections as pseudo-labels.

Statistical justification for Gram-Schmidt over PCA:
  - PCA mixes physical axes; a "PC1 event" is uninterpretable to the scorer.
  - Gram-Schmidt preserves lat_accel as the primary reference (most stable
    and directly linked to cornering), then sequentially removes shared
    variance in lon and vert.  Each residual channel retains physical units
    (m/s²) and an unambiguous interpretation.
"""

from __future__ import annotations

import numpy as np
from typing import Dict, List, Optional, Tuple


# ── Feature names ──────────────────────────────────────────────────────────────

FEATURE_NAMES = ["lat_g", "lon_g", "vert_g", "jerk", "grip_g", "speed_n"]


# ────────────────────────────────────────────────────────────────────────────────
# Module 0: Force Decorrelator  (physics layer — run BEFORE evaluation modules)
# ────────────────────────────────────────────────────────────────────────────────

class ForceDecorrelator:
    """
    Gram-Schmidt sequential partial regression over the vehicle force axes.

    Three sources of spurious correlation are removed in order:

      Step 1:  lon ~ lat
               Phone orientation or simultaneous cornering + braking couples the
               longitudinal and lateral axes.  Project out the component of
               long_accel linearly explained by lat_accel.

      Step 2:  vert ~ lat
               Gravity compensation is imperfect when the phone is tilted
               transversely; vertical bleeding into the lateral plane.

      Step 3:  vert ~ lon_orth  (after Step 1)
               Forward tilt couples vert to the (now-clean) longitudinal axis.

    After projection the three channels are pairwise orthogonal by construction.
    Jerk is RECOMPUTED from the decorrelated acceleration vector so that jerk
    from sustained steady-state cornering (expected) no longer inflates the
    erratic-control score.

    Physical units (m/s²) are preserved throughout; thresholds need not change.
    """

    def __init__(self, min_samples: int = 50):
        self._min_samples = min_samples
        self._b_lon_lat:   float = 0.0
        self._b_vert_lat:  float = 0.0
        self._b_vert_lon:  float = 0.0
        self._fitted: bool = False
        # Diagnostics exposed to the evaluation result
        self.coupling:       Dict[str, float] = {}
        self.var_reduction:  Dict[str, float] = {}
        self.r_squared:      Dict[str, float] = {}

    # ── fit ───────────────────────────────────────────────────────────────────

    def fit(self, physics_data: List[Dict]) -> "ForceDecorrelator":
        if len(physics_data) < self._min_samples:
            return self

        lat  = np.array([p.get("lat_accel",  0.0) for p in physics_data], dtype=np.float64)
        lon  = np.array([p.get("long_accel", 0.0) for p in physics_data], dtype=np.float64)
        vert = np.array([p.get("vert_accel", 0.0) for p in physics_data], dtype=np.float64)

        eps = 1e-12

        # ── Step 1: lon ~ lat ─────────────────────────────────────────────────
        var_lat = float(np.var(lat)) + eps
        cov_lon_lat = float(np.cov(lon, lat, ddof=1)[0, 1])
        self._b_lon_lat = cov_lon_lat / var_lat
        lon_orth = lon - self._b_lon_lat * lat

        r2_lon = (cov_lon_lat ** 2) / (var_lat * (float(np.var(lon)) + eps))

        # ── Step 2: vert ~ lat ───────────────────────────────────────────────
        cov_vert_lat = float(np.cov(vert, lat, ddof=1)[0, 1])
        self._b_vert_lat = cov_vert_lat / var_lat

        # ── Step 3: vert ~ lon_orth ──────────────────────────────────────────
        vert_partial = vert - self._b_vert_lat * lat
        var_lon_orth  = float(np.var(lon_orth)) + eps
        cov_vert_lon  = float(np.cov(vert_partial, lon_orth, ddof=1)[0, 1])
        self._b_vert_lon = cov_vert_lon / var_lon_orth
        vert_orth = vert_partial - self._b_vert_lon * lon_orth

        r2_vert = 1.0 - (float(np.var(vert_orth)) / (float(np.var(vert)) + eps))

        # ── Diagnostics ──────────────────────────────────────────────────────
        self.coupling = {
            "lon_lat":   round(self._b_lon_lat,  4),
            "vert_lat":  round(self._b_vert_lat, 4),
            "vert_lon":  round(self._b_vert_lon, 4),
        }
        self.var_reduction = {
            "lon":  round(1.0 - float(np.var(lon_orth))  / (float(np.var(lon))  + eps), 4),
            "vert": round(1.0 - float(np.var(vert_orth)) / (float(np.var(vert)) + eps), 4),
        }
        self.r_squared = {
            "lon_from_lat":  round(max(0.0, float(r2_lon)),  4),
            "vert_from_lat_lon": round(max(0.0, float(r2_vert)), 4),
        }
        self._fitted = True
        return self

    # ── transform ─────────────────────────────────────────────────────────────

    def transform(self, physics_data: List[Dict]) -> List[Dict]:
        """
        Return a new list of physics dicts with decorrelated long_accel,
        vert_accel, and recomputed jerk.  The input list is never mutated.
        """
        if not self._fitted:
            return physics_data

        out: List[Dict] = []
        prev_vec: Optional[np.ndarray] = None
        prev_ts_s: float = 0.0

        for p in physics_data:
            lat  = float(p.get("lat_accel",  0.0))
            lon  = float(p.get("long_accel", 0.0))
            vert = float(p.get("vert_accel", 0.0))
            ts_s = float(p.get("timestamp", 0)) / 1000.0

            lon_orth  = lon  - self._b_lon_lat  * lat
            vert_temp = vert - self._b_vert_lat * lat
            lon_orth_here = lon - self._b_lon_lat * lat   # same as lon_orth above
            vert_orth = vert_temp - self._b_vert_lon * lon_orth_here

            dec_vec = np.array([lat, lon_orth, vert_orth], dtype=np.float64)

            # Recompute jerk from decorrelated acceleration vector
            dt = ts_s - prev_ts_s
            if prev_vec is not None and dt > 0.001:
                jerk = float(np.linalg.norm(dec_vec - prev_vec) / dt)
            else:
                jerk = 0.0

            entry = dict(p)
            # Preserve raw values so friction-circle calculation (which measures
            # total in-plane force from genuinely combined manoeuvres) is unaffected.
            entry["raw_long_accel"] = float(lon)
            entry["raw_vert_accel"] = float(vert)
            entry["long_accel"] = float(lon_orth)
            entry["vert_accel"] = float(vert_orth)
            entry["jerk"]       = jerk
            out.append(entry)

            prev_vec  = dec_vec
            prev_ts_s = ts_s

        return out

    def fit_transform(self, physics_data: List[Dict]) -> List[Dict]:
        return self.fit(physics_data).transform(physics_data)

    def report(self) -> Dict:
        return {
            "fitted":        self._fitted,
            "coupling":      self.coupling,
            "var_reduction": self.var_reduction,
            "r_squared":     self.r_squared,
        }


# ── Feature extraction (uses decorrelated physics_data) ───────────────────────

def _extract_sample_features(physics_data: List[Dict]) -> np.ndarray:
    """
    Returns (N, 6) float32 feature matrix.
    Because physics_data has already been decorrelated, grip_g = sqrt(lat²+lon²)
    is the true in-plane utilisation (lateral and longitudinal are orthogonal).
    """
    rows = []
    for p in physics_data:
        lat_g  = abs(p.get("lat_accel",  0.0)) / 9.81
        lon_g  = abs(p.get("long_accel", 0.0)) / 9.81
        vert_g = abs(p.get("vert_accel", 0.0)) / 9.81
        jerk   = min(p.get("jerk", 0.0), 50.0)
        grip_g = np.sqrt(lat_g ** 2 + lon_g ** 2)
        speed  = p.get("speed", 0.0) / 120.0
        rows.append([lat_g, lon_g, vert_g, jerk, grip_g, speed])
    return np.array(rows, dtype=np.float32)


def _fault_labels(physics_data: List[Dict], events: List[Dict],
                  window_ms: float = 500.0) -> np.ndarray:
    fault_times = np.array([e.get("timestamp", 0) for e in events], dtype=float)
    labels = np.zeros(len(physics_data), dtype=np.float32)
    for i, p in enumerate(physics_data):
        ts = float(p.get("timestamp", 0))
        if fault_times.size and np.any(np.abs(fault_times - ts) < window_ms):
            labels[i] = 1.0
    return labels


# ── Ridge regression helpers ───────────────────────────────────────────────────

def _ridge_solve(X: np.ndarray, y: np.ndarray, alpha: float = 1.0) -> np.ndarray:
    n_feat = X.shape[1]
    A = X.T @ X + alpha * np.eye(n_feat, dtype=np.float64)
    b = X.T @ y.astype(np.float64)
    return np.linalg.solve(A, b).astype(np.float32)


def _whiten(X: np.ndarray) -> Tuple[np.ndarray, np.ndarray, np.ndarray]:
    """
    Cholesky whitening: X_w = (X - μ) @ L^{-T}  where Σ = L L^T.
    Returns whitened X, mean vector, and inverse-Cholesky factor.
    Columns with near-zero variance are standardised instead.
    """
    mu  = X.mean(axis=0)
    Xc  = (X - mu).astype(np.float64)
    cov = Xc.T @ Xc / max(len(Xc) - 1, 1)
    # Regularise diagonal to prevent near-singular decomposition
    cov += np.eye(cov.shape[0]) * 1e-6
    try:
        L       = np.linalg.cholesky(cov)
        L_inv_T = np.linalg.inv(L).T
        Xw      = (Xc @ L_inv_T).astype(np.float32)
    except np.linalg.LinAlgError:
        # Fallback: simple standardisation
        std     = Xc.std(axis=0) + 1e-8
        L_inv_T = np.diag(1.0 / std)
        Xw      = (Xc / std).astype(np.float32)
    return Xw, mu, L_inv_T


# ────────────────────────────────────────────────────────────────────────────────
# Module 1: Ridge Correlation Analyser
# ────────────────────────────────────────────────────────────────────────────────

class ForceRidgeAnalyzer:
    """
    Fits Ridge regression on whitened per-ride features using physics-detected
    faults as pseudo-labels.  Cholesky whitening ensures no feature dominates
    the regression due to scale differences.

    Outputs:
      • per-sample fault risk score  [0, 1]
      • correlation matrix between decorrelated force metrics
      • per-feature importance weights
      • calibrated thresholds scaled by importance
    """

    def __init__(self, ridge_alpha: float = 1.0):
        self._alpha = ridge_alpha
        self._weights: Optional[np.ndarray] = None
        self._bias: float = 0.0
        self._mu:      Optional[np.ndarray] = None
        self._L_inv_T: Optional[np.ndarray] = None
        self.correlation_matrix: Optional[np.ndarray] = None

    def fit(self, physics_data: List[Dict], fault_events: List[Dict]) -> "ForceRidgeAnalyzer":
        X = _extract_sample_features(physics_data)
        y = _fault_labels(physics_data, fault_events)
        if X.shape[0] < 20:
            return self

        Xw, self._mu, self._L_inv_T = _whiten(X)
        self.correlation_matrix = np.corrcoef(Xw.T)
        self._weights = _ridge_solve(Xw, y, self._alpha)
        self._bias    = float(y.mean() - Xw.mean(axis=0) @ self._weights)
        return self

    def risk_scores(self, physics_data: List[Dict]) -> np.ndarray:
        if self._weights is None:
            return np.zeros(len(physics_data), dtype=np.float32)
        X  = _extract_sample_features(physics_data)
        Xw = ((X - self._mu) @ self._L_inv_T).astype(np.float32)
        return np.clip(Xw @ self._weights + self._bias, 0.0, 1.0)

    def calibrate(self, base: Dict[str, float]) -> Dict[str, float]:
        """
        Reduce thresholds for features whose ridge importance exceeds the
        60th percentile.  Max reduction is capped at 15 % to prevent
        oversensitivity in noisy environments.
        """
        if self._weights is None:
            return base

        w_abs  = np.abs(self._weights)
        w_norm = w_abs / (w_abs.max() + 1e-8)
        p60    = float(np.percentile(w_norm, 60))

        feat_to_thresh = {
            "lon_g":  "braking",
            "lat_g":  "cornering",
            "jerk":   "jerk",
            "grip_g": "grip",
            "vert_g": "vertical",
        }
        out = dict(base)
        for idx, name in enumerate(FEATURE_NAMES):
            key = feat_to_thresh.get(name)
            if not key or key not in out:
                continue
            imp = float(w_norm[idx])
            if imp > p60:
                factor = 1.0 - 0.15 * (imp - p60) / max(1.0 - p60, 1e-3)
                out[key] = float(out[key]) * max(0.75, factor)
        return out

    def top_correlations(self, n: int = 5) -> List[Tuple[str, str, float]]:
        if self.correlation_matrix is None:
            return []
        pairs: List[Tuple[str, str, float]] = []
        for i in range(len(FEATURE_NAMES)):
            for j in range(i + 1, len(FEATURE_NAMES)):
                val = float(self.correlation_matrix[i, j])
                if np.isfinite(val):
                    pairs.append((FEATURE_NAMES[i], FEATURE_NAMES[j], val))
        pairs.sort(key=lambda x: abs(x[2]), reverse=True)
        return pairs[:n]


# ────────────────────────────────────────────────────────────────────────────────
# Module 2: Echo State Network
# ────────────────────────────────────────────────────────────────────────────────

class ForceESN:
    """
    Echo State Network: fixed random reservoir + trainable Ridge readout.

    The leaky-integrator reservoir propagates temporal context of the force
    sequence into a high-dimensional state space.  Only the linear W_out is
    trained (via Ridge) — no backpropagation required.

    Input features are Cholesky-whitened before entering the reservoir so
    that no single force dimension dominates the reservoir dynamics.
    """

    def __init__(
        self,
        input_size:      int   = 6,
        reservoir_size:  int   = 64,
        spectral_radius: float = 0.9,
        input_scaling:   float = 0.5,
        leaking_rate:    float = 0.3,
        ridge_alpha:     float = 1e-4,
        window:          int   = 30,
        seed:            int   = 0,
    ):
        self._n_in   = input_size
        self._n_res  = reservoir_size
        self._lr     = leaking_rate
        self._window = window
        self._alpha  = ridge_alpha
        self._trained = False

        rng = np.random.default_rng(seed)
        self._W_in = rng.uniform(-input_scaling, input_scaling,
                                  (reservoir_size, input_size)).astype(np.float32)
        W = rng.standard_normal((reservoir_size, reservoir_size)).astype(np.float32)
        eigvals = np.linalg.eigvals(W)
        W *= spectral_radius / float(np.abs(eigvals).max())
        self._W_res = W.astype(np.float32)

        self._W_out:   Optional[np.ndarray] = None
        self._bias_out: float = 0.5
        self._mu:      Optional[np.ndarray] = None
        self._L_inv_T: Optional[np.ndarray] = None

    def _reservoir(self, X: np.ndarray) -> np.ndarray:
        T = X.shape[0]
        H = np.zeros((T, self._n_res), dtype=np.float32)
        h = np.zeros(self._n_res, dtype=np.float32)
        for t in range(T):
            h = ((1.0 - self._lr) * h
                 + self._lr * np.tanh(self._W_in @ X[t] + self._W_res @ h))
            H[t] = h
        return H

    def _window_avg(self, H: np.ndarray) -> np.ndarray:
        T, n = H.shape
        w = self._window
        if T < w:
            return np.zeros((0, n), dtype=np.float32)
        out = np.zeros((T - w + 1, n), dtype=np.float32)
        for i in range(T - w + 1):
            out[i] = H[i : i + w].mean(axis=0)
        return out

    def fit(self, physics_data: List[Dict], fault_events: List[Dict]) -> "ForceESN":
        X = _extract_sample_features(physics_data)
        y = _fault_labels(physics_data, fault_events)
        if X.shape[0] < self._window + 10:
            return self

        Xw, self._mu, self._L_inv_T = _whiten(X)
        H     = self._reservoir(Xw)
        H_win = self._window_avg(H)
        y_win = y[self._window - 1 :].astype(np.float32)

        self._W_out   = _ridge_solve(H_win, y_win, self._alpha)
        self._bias_out = float(y_win.mean() - H_win.mean(axis=0) @ self._W_out)
        self._trained  = True
        return self

    def predict(self, physics_data: List[Dict]) -> np.ndarray:
        n = len(physics_data)
        if not self._trained or n < self._window:
            return np.zeros(n, dtype=np.float32)
        X  = _extract_sample_features(physics_data)
        Xw = ((X - self._mu) @ self._L_inv_T).astype(np.float32)
        H     = self._reservoir(Xw)
        H_win = self._window_avg(H)
        raw   = H_win @ self._W_out + self._bias_out
        probs = (1.0 / (1.0 + np.exp(-np.clip(raw, -15, 15)))).astype(np.float32)
        out = np.zeros(n, dtype=np.float32)
        out[self._window - 1 :] = probs
        return out

    def detect_events(
        self,
        physics_data: List[Dict],
        threshold:    float = 0.55,
        cooldown_ms:  float = 3000.0,
    ) -> List[Dict]:
        probs = self.predict(physics_data)
        if not probs.any():
            return []

        events: List[Dict] = []
        last_ts = -cooldown_ms

        for i, (p, prob) in enumerate(zip(physics_data, probs)):
            if prob < threshold:
                continue
            ts = float(p.get("timestamp", 0))
            if ts - last_ts < cooldown_ms:
                continue
            last_ts = ts

            ws = max(0, i - 5)
            sl = physics_data[ws : i + 1]
            forces = {
                "harsh_cornering": max(abs(q.get("lat_accel",  0)) / 9.81 for q in sl),
                "harsh_braking":   max(abs(q.get("long_accel", 0)) / 9.81 for q in sl),
                "vertical_impact": max(abs(q.get("vert_accel", 0)) / 9.81 for q in sl),
                "erratic_control": max(q.get("jerk", 0) for q in sl) / 10.0,
            }
            dominant = max(forces, key=forces.get)
            events.append({
                "type":        f"esn_{dominant}",
                "timestamp":   ts,
                "lat":         p.get("latitude"),
                "lng":         p.get("longitude"),
                "probability": round(float(prob), 3),
                "description": f"ESN {dominant.replace('_', ' ')} (p={prob:.2f})",
            })
        return events


# ────────────────────────────────────────────────────────────────────────────────
# Convenience: run full ML pipeline on a ride
# ────────────────────────────────────────────────────────────────────────────────

def analyse_ride(
    physics_data:      List[Dict],
    existing_events:   List[Dict],
    base_thresholds:   Optional[Dict[str, float]] = None,
    decorr_report:     Optional[Dict] = None,
) -> Dict:
    """
    Run Ridge analyser and ESN on (already-decorrelated) physics_data.

    Returns:
      calibrated_thresholds  — per-ride adjusted thresholds
      esn_events             — additional temporal-pattern events
      top_correlations       — top 5 post-decorrelation metric correlations
      ridge_weights          — {feature: abs_importance}
      esn_trained            — bool
      decorrelation          — ForceDecorrelator.report() (passed through)
    """
    if base_thresholds is None:
        base_thresholds = {
            "braking":   0.6 * 9.81,
            "cornering": 0.45 * 9.81,
            "jerk":      2.0,
            "grip":      0.60 * 9.81,
            "vertical":  0.40 * 9.81,
        }

    result: Dict = {
        "calibrated_thresholds": dict(base_thresholds),
        "esn_events":            [],
        "top_correlations":      [],
        "ridge_weights":         {},
        "esn_trained":           False,
        "decorrelation":         decorr_report or {},
    }

    try:
        ridge = ForceRidgeAnalyzer()
        ridge.fit(physics_data, existing_events)
        result["calibrated_thresholds"] = ridge.calibrate(base_thresholds)
        result["top_correlations"]      = ridge.top_correlations()
        if ridge._weights is not None:
            result["ridge_weights"] = {
                name: round(float(abs(w)), 4)
                for name, w in zip(FEATURE_NAMES, ridge._weights)
            }
    except Exception:
        pass

    try:
        esn = ForceESN()
        esn.fit(physics_data, existing_events)
        result["esn_events"]  = esn.detect_events(physics_data)
        result["esn_trained"] = esn._trained
    except Exception:
        pass

    return result
