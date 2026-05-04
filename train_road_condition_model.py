"""
Train the Road Condition Classifier.

Extracts Z-axis IMU features from existing diagnostic_rides acceleration data,
labels windows with a threshold detector as seed labels, trains a quantized
Random Forest, and exports to ONNX for on-device inference.

Model specs:
  - Input:  5 features over a 20-sample (2 s at 10 Hz) Z-axis sliding window
      1. RMSA         — Root Mean Square Acceleration (correlated with IRI)
      2. peak_amp     — Peak-to-peak amplitude
      3. variance     — Z-axis variance
      4. hp_energy    — High-pass filtered energy (>2 Hz removes sway, keeps impacts)
      5. speed_kmh    — Speed at window midpoint (used in backend confidence weighting)
  - Output: 5-class label  smooth / rough / bump / pothole / speed_bump
  - Target: < 500 KB ONNX, < 5 ms inference

Usage:
    python train_road_condition_model.py                   # synthetic data only
    python train_road_condition_model.py --rides-db PATH   # + real SQLite data
    python train_road_condition_model.py --output DIR      # custom output dir

Output:
    mobile-app-kmp/androidApp/src/androidMain/assets/road_condition_classifier.onnx
    app/models/road_condition_labels.json
"""

import argparse
import json
import os
import sys

import numpy as np
from scipy.signal import butter, filtfilt

# ── Constants ────────────────────────────────────────────────────────────────────

WINDOW_SIZE   = 20      # samples (2 s at 10 Hz)
SAMPLE_HZ     = 10.0
HP_CUTOFF_HZ  = 2.0     # high-pass cutoff to strip suspension sway

CLASSES = ["smooth", "rough", "bump", "pothole", "speed_bump"]
CLASS_INDEX = {c: i for i, c in enumerate(CLASSES)}

# Seed-label thresholds (Z-axis in m/s²)
ROUGH_VARIANCE_THRESHOLD      = 0.8   # m²/s⁴
BUMP_PEAK_THRESHOLD           = 3.5   # m/s²
POTHOLE_PEAK_THRESHOLD        = 5.0   # m/s²
SPEED_BUMP_PEAK_THRESHOLD     = 4.0   # m/s²  (sustained, not spike)
SPEED_BUMP_DURATION_SAMPLES   = 5     # must stay above threshold for this many samples


# ── High-pass filter ─────────────────────────────────────────────────────────────

def _highpass(signal: np.ndarray, cutoff: float = HP_CUTOFF_HZ, fs: float = SAMPLE_HZ) -> np.ndarray:
    nyq = fs / 2.0
    b, a = butter(2, cutoff / nyq, btype="high", analog=False)
    if len(signal) < 15:  # filtfilt needs enough samples for the filter order
        return signal - signal.mean()
    return filtfilt(b, a, signal)


# ── Feature extraction ───────────────────────────────────────────────────────────

def extract_features(z_window: np.ndarray, speed_kmh: float) -> np.ndarray:
    """Return 5-feature vector for one 20-sample window."""
    rmsa       = float(np.sqrt(np.mean(z_window ** 2)))
    peak_amp   = float(np.max(z_window) - np.min(z_window))
    variance   = float(np.var(z_window))
    hp_signal  = _highpass(z_window)
    hp_energy  = float(np.mean(hp_signal ** 2))
    return np.array([rmsa, peak_amp, variance, hp_energy, float(speed_kmh)], dtype=np.float32)


# ── Seed labelling ───────────────────────────────────────────────────────────────

def _seed_label(z_window: np.ndarray) -> int:
    """Assign a class index using simple threshold rules."""
    peak = np.max(np.abs(z_window))
    var  = float(np.var(z_window))
    hp   = _highpass(z_window)
    hp_e = float(np.mean(hp ** 2))

    # Speed bump: sustained elevation (slow-moving impact)
    above = np.sum(np.abs(z_window) > SPEED_BUMP_PEAK_THRESHOLD)
    if above >= SPEED_BUMP_DURATION_SAMPLES and peak < POTHOLE_PEAK_THRESHOLD:
        return CLASS_INDEX["speed_bump"]

    if peak >= POTHOLE_PEAK_THRESHOLD and hp_e > 1.5:
        return CLASS_INDEX["pothole"]
    if peak >= BUMP_PEAK_THRESHOLD and hp_e > 0.8:
        return CLASS_INDEX["bump"]
    if var >= ROUGH_VARIANCE_THRESHOLD:
        return CLASS_INDEX["rough"]
    return CLASS_INDEX["smooth"]


# ── Synthetic data generation ────────────────────────────────────────────────────

def _generate_synthetic(n_per_class: int = 2000) -> tuple[np.ndarray, np.ndarray]:
    rng = np.random.default_rng(42)
    X, y = [], []

    def _add(z_windows, label):
        for z in z_windows:
            X.append(extract_features(z, rng.uniform(10, 80)))
            y.append(CLASS_INDEX[label])

    # smooth — low-amplitude Gaussian noise
    _add([rng.normal(0, 0.3, WINDOW_SIZE) for _ in range(n_per_class)], "smooth")

    # rough — moderate variance, correlated noise
    _add([np.cumsum(rng.normal(0, 0.4, WINDOW_SIZE)) * 0.5 for _ in range(n_per_class)], "rough")

    # bump — sharp single spike
    def _bump():
        z = rng.normal(0, 0.2, WINDOW_SIZE)
        z[rng.integers(5, 15)] += rng.uniform(3.5, 4.8)
        return z
    _add([_bump() for _ in range(n_per_class)], "bump")

    # pothole — sharp deep spike
    def _pothole():
        z = rng.normal(0, 0.2, WINDOW_SIZE)
        z[rng.integers(5, 15)] += rng.choice([-1, 1]) * rng.uniform(5.0, 8.0)
        return z
    _add([_pothole() for _ in range(n_per_class)], "pothole")

    # speed bump — sustained raised profile
    def _speed_bump():
        z = rng.normal(0, 0.2, WINDOW_SIZE)
        start = rng.integers(3, 8)
        z[start:start + 7] += rng.uniform(4.0, 5.5)
        return z
    _add([_speed_bump() for _ in range(n_per_class)], "speed_bump")

    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int64)


# ── Real data loading from SQLite ────────────────────────────────────────────────

def _load_from_sqlite(db_path: str) -> tuple[np.ndarray, np.ndarray]:
    import sqlite3
    import json as _json

    X, y = [], []
    try:
        conn = sqlite3.connect(db_path)
        cur = conn.cursor()
        cur.execute("SELECT acceleration_data, speed_data FROM diagnostic_rides WHERE acceleration_data IS NOT NULL LIMIT 5000")
        rows = cur.fetchall()
        conn.close()
    except Exception as e:
        print(f"[warn] Could not read from {db_path}: {e}")
        return np.empty((0, 5), dtype=np.float32), np.empty((0,), dtype=np.int64)

    for accel_json, speed_json in rows:
        try:
            accels = _json.loads(accel_json)  # [{x, y, z, timestamp}, ...]
            speeds = _json.loads(speed_json) if speed_json else []

            z_series = np.array([a.get("z", 0.0) for a in accels], dtype=np.float32)
            speed_series = np.array([s.get("speed", 0.0) * 3.6 for s in speeds], dtype=np.float32)  # m/s → km/h

            for i in range(0, len(z_series) - WINDOW_SIZE + 1, WINDOW_SIZE // 2):
                window = z_series[i:i + WINDOW_SIZE]
                if len(window) < WINDOW_SIZE:
                    break
                # Interpolate speed for window midpoint
                mid = i + WINDOW_SIZE // 2
                speed = float(speed_series[min(mid, len(speed_series) - 1)]) if len(speed_series) > 0 else 30.0

                X.append(extract_features(window, speed))
                y.append(_seed_label(window))
        except Exception:
            continue

    if not X:
        return np.empty((0, 5), dtype=np.float32), np.empty((0,), dtype=np.int64)
    return np.array(X, dtype=np.float32), np.array(y, dtype=np.int64)


# ── Training ─────────────────────────────────────────────────────────────────────

def train(
    X: np.ndarray,
    y: np.ndarray,
    n_estimators: int = 50,
    max_depth: int = 8,
) -> "sklearn.ensemble.RandomForestClassifier":  # type: ignore[name-defined]
    from sklearn.ensemble import RandomForestClassifier
    from sklearn.model_selection import cross_val_score

    clf = RandomForestClassifier(
        n_estimators=n_estimators,
        max_depth=max_depth,
        min_samples_leaf=5,
        n_jobs=-1,
        random_state=42,
    )
    clf.fit(X, y)

    scores = cross_val_score(clf, X, y, cv=5, scoring="f1_macro")
    print(f"[train] 5-fold macro-F1: {scores.mean():.3f} ± {scores.std():.3f}")

    return clf


# ── ONNX export ──────────────────────────────────────────────────────────────────

def export_onnx(clf, output_path: str) -> None:
    from skl2onnx import convert_sklearn
    from skl2onnx.common.data_types import FloatTensorType

    initial_type = [("float_input", FloatTensorType([None, 5]))]
    onnx_model = convert_sklearn(
        clf,
        initial_types=initial_type,
        options={type(clf): {"zipmap": False}},  # return raw label indices
    )

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "wb") as f:
        f.write(onnx_model.SerializeToString())

    size_kb = os.path.getsize(output_path) / 1024
    print(f"[export] ONNX model written to {output_path} ({size_kb:.1f} KB)")
    if size_kb > 500:
        print(f"[warn] Model exceeds 500 KB target ({size_kb:.1f} KB). Reduce n_estimators or max_depth.")


def verify_onnx(onnx_path: str, X_sample: np.ndarray) -> None:
    import onnxruntime as ort

    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    input_name = sess.get_inputs()[0].name
    out = sess.run(None, {input_name: X_sample[:5]})
    preds = [CLASSES[i] for i in out[0]]
    print(f"[verify] Sample predictions: {preds}")


# ── CLI ──────────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(description="Train road condition ONNX classifier")
    parser.add_argument("--rides-db", default=None, help="Path to roadready.db SQLite file")
    parser.add_argument(
        "--output",
        default="mobile-app-kmp/androidApp/src/androidMain/assets",
        help="Directory for ONNX model output",
    )
    args = parser.parse_args()

    print("[data] Generating synthetic training data …")
    X_syn, y_syn = _generate_synthetic(n_per_class=2000)
    print(f"[data] Synthetic: {len(X_syn)} samples")

    X_real, y_real = np.empty((0, 5), dtype=np.float32), np.empty((0,), dtype=np.int64)
    if args.rides_db:
        print(f"[data] Loading real ride data from {args.rides_db} …")
        X_real, y_real = _load_from_sqlite(args.rides_db)
        print(f"[data] Real: {len(X_real)} samples")

    X = np.concatenate([X_syn, X_real], axis=0) if len(X_real) > 0 else X_syn
    y = np.concatenate([y_syn, y_real], axis=0) if len(y_real) > 0 else y_syn

    from collections import Counter
    dist = Counter(CLASSES[i] for i in y)
    print(f"[data] Class distribution: {dict(dist)}")
    print(f"[train] Training Random Forest on {len(X)} samples …")

    clf = train(X, y)

    onnx_path = os.path.join(args.output, "road_condition_classifier.onnx")
    export_onnx(clf, onnx_path)
    verify_onnx(onnx_path, X)

    labels_path = "app/models/road_condition_labels.json"
    os.makedirs("app/models", exist_ok=True)
    with open(labels_path, "w") as f:
        json.dump({"classes": CLASSES, "window_size": WINDOW_SIZE, "sample_hz": SAMPLE_HZ}, f, indent=2)
    print(f"[export] Labels metadata written to {labels_path}")


if __name__ == "__main__":
    main()
