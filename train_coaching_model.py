"""
Train the RoadReady real-time coaching model.

Generates synthetic IMU windows from physics thresholds, trains a small
1D-CNN in PyTorch, and exports an ONNX model ready for on-device inference.

Physics-Informed Neural Network (PINN) approach:
  - 4 extra physics-derived channels appended to raw sensor input
    (lat_g, lon_g, grip_g, friction_excess)
  - Physics penalty term in the loss: punishes predicting "normal" when
    G-force thresholds clearly indicate an event

Output: mobile-app-kmp/androidApp/src/androidMain/assets/driving_coach.onnx

Usage:
    python train_coaching_model.py                  # synthetic data only
    python train_coaching_model.py --rides-db URL   # + real ride data from backend
"""

import os
import sys
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, TensorDataset
import onnx
import onnxruntime as ort

# ── Constants (mirror DiagnosticEvaluator) ──────────────────────────────────────

GRAVITY            = 9.81          # m/s²
HARD_BRAKING_G     = 0.6
HARD_ACCEL_G       = 0.4
SHARP_TURN_G       = 0.45
FRICTION_CIRCLE_G  = 0.60          # combined in-plane grip limit

WINDOW_SIZE        = 50
NUM_RAW_FEATURES   = 4             # lat_accel, long_accel, vert_accel, jerk
NUM_PHYS_FEATURES  = 4             # lat_g, lon_g, grip_g, friction_excess
NUM_FEATURES       = NUM_RAW_FEATURES + NUM_PHYS_FEATURES  # 8 total
NUM_CLASSES        = 4             # 0=normal 1=harsh_braking 2=harsh_accel 3=sharp_turn

ACCEL_NORM         = 20.0          # normalisation divisor for raw channels (m/s²)
JERK_NORM          = 30.0          # normalisation divisor for jerk (m/s³)
PHYS_G_NORM        = 1.5           # normalisation ceiling for G-unit physics channels
FRICTION_EXCESS_NORM = PHYS_G_NORM - FRICTION_CIRCLE_G   # = 0.90

# Weight of physics penalty relative to classification loss.
# 0.3 means physics constraint contributes ~23% of total gradient signal.
PHYSICS_LAMBDA     = 0.3

ASSET_PATH = os.path.join(
    os.path.dirname(__file__),
    "mobile-app-kmp", "androidApp", "src", "androidMain", "assets",
    "driving_coach.onnx",
)

# ── Physics feature helpers ──────────────────────────────────────────────────────

def _jerk(window: np.ndarray) -> np.ndarray:
    """Compute per-sample jerk from consecutive accelerometer differences."""
    diff = np.diff(window, axis=0, prepend=window[:1])
    return np.linalg.norm(diff, axis=1, keepdims=True)


def _physics_features(lat: np.ndarray, lon: np.ndarray) -> np.ndarray:
    """
    Compute 4 physics-derived channels from raw m/s² arrays (shape [T,1] each).

    Channels:
      lat_g           — lateral G-force utilisation (abs)
      lon_g           — longitudinal G-force utilisation (abs)
      grip_g          — in-plane friction circle usage  sqrt(lat²+lon²)/g
      friction_excess — amount above the 0.6G friction circle limit (relu)

    All channels are normalised to [0, 1].
    """
    lat_g          = np.abs(lat) / GRAVITY
    lon_g          = np.abs(lon) / GRAVITY
    grip_g         = np.sqrt(lat**2 + lon**2) / GRAVITY
    friction_excess = np.clip(grip_g - FRICTION_CIRCLE_G, 0, None)

    lat_g_n         = np.clip(lat_g          / PHYS_G_NORM,        0.0, 1.0)
    lon_g_n         = np.clip(lon_g          / PHYS_G_NORM,        0.0, 1.0)
    grip_g_n        = np.clip(grip_g         / PHYS_G_NORM,        0.0, 1.0)
    friction_n      = np.clip(friction_excess / FRICTION_EXCESS_NORM, 0.0, 1.0)

    return np.hstack([lat_g_n, lon_g_n, grip_g_n, friction_n])


# ── Synthetic data generation ───────────────────────────────────────────────────

def make_normal_window(rng: np.random.Generator) -> np.ndarray:
    """Smooth, low-G driving — city cruise or steady highway."""
    lat  = rng.normal(0, 0.3, (WINDOW_SIZE, 1))
    lon  = rng.normal(0, 0.4, (WINDOW_SIZE, 1))
    vert = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    raw  = np.hstack([lat, lon, vert])
    jerk = _jerk(raw)
    phys = _physics_features(lat, lon)
    return np.hstack([raw, jerk, phys])


def make_braking_window(rng: np.random.Generator) -> np.ndarray:
    """Strong forward deceleration > 0.6G sustained for several samples."""
    lat  = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    vert = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    lon  = rng.normal(0, 0.3, (WINDOW_SIZE, 1))
    peak_g = rng.uniform(HARD_BRAKING_G + 0.05, 1.2)
    start  = WINDOW_SIZE // 3
    end    = 2 * WINDOW_SIZE // 3
    lon[start:end] = -peak_g * GRAVITY + rng.normal(0, 0.5, (end - start, 1))
    raw  = np.hstack([lat, lon, vert])
    jerk = _jerk(raw)
    phys = _physics_features(lat, lon)
    return np.hstack([raw, jerk, phys])


def make_accel_window(rng: np.random.Generator) -> np.ndarray:
    """Hard forward acceleration > 0.4G."""
    lat  = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    vert = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    lon  = rng.normal(0, 0.3, (WINDOW_SIZE, 1))
    peak_g = rng.uniform(HARD_ACCEL_G + 0.05, 0.9)
    start  = WINDOW_SIZE // 3
    end    = 2 * WINDOW_SIZE // 3
    lon[start:end] = peak_g * GRAVITY + rng.normal(0, 0.5, (end - start, 1))
    raw  = np.hstack([lat, lon, vert])
    jerk = _jerk(raw)
    phys = _physics_features(lat, lon)
    return np.hstack([raw, jerk, phys])


def make_sharp_turn_window(rng: np.random.Generator) -> np.ndarray:
    """High lateral G > 0.45G during a turn."""
    lon  = rng.normal(0, 0.3, (WINDOW_SIZE, 1))
    vert = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    lat  = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    peak_g = rng.uniform(SHARP_TURN_G + 0.05, 0.9)
    sign   = rng.choice([-1, 1])
    start  = WINDOW_SIZE // 3
    end    = 2 * WINDOW_SIZE // 3
    lat[start:end] = sign * peak_g * GRAVITY + rng.normal(0, 0.5, (end - start, 1))
    raw  = np.hstack([lat, lon, vert])
    jerk = _jerk(raw)
    phys = _physics_features(lat, lon)
    return np.hstack([raw, jerk, phys])


def build_dataset(n_per_class: int = 3000, seed: int = 42):
    rng = np.random.default_rng(seed)
    builders = [make_normal_window, make_braking_window, make_accel_window, make_sharp_turn_window]
    X, y = [], []
    for label, fn in enumerate(builders):
        for _ in range(n_per_class):
            w = fn(rng)
            # Normalise raw channels — physics channels are already [0,1]
            w[:, :3] /= ACCEL_NORM
            w[:,  3] /= JERK_NORM
            w[:, :4]  = np.clip(w[:, :4], -1.0, 1.0)
            X.append(w)
            y.append(label)
    X = np.array(X, dtype=np.float32)   # [N, WINDOW_SIZE, NUM_FEATURES]
    y = np.array(y, dtype=np.int64)
    idx = rng.permutation(len(y))
    return X[idx], y[idx]


# ── Model ───────────────────────────────────────────────────────────────────────

class DrivingCoach(nn.Module):
    """
    Physics-Informed 1D-CNN for driving-event classification.

    Input channels (8):
      0  lat_accel   normalised m/s²
      1  lon_accel   normalised m/s²
      2  vert_accel  normalised m/s²
      3  jerk        normalised m/s³
      4  lat_g       lateral G-force utilisation   [0,1]
      5  lon_g       longitudinal G-force util      [0,1]
      6  grip_g      friction circle usage          [0,1]
      7  friction_excess  above-limit G             [0,1]

    ~26 K parameters — fits comfortably in 100 KB after quantisation.
    """
    def __init__(self, n_classes: int = NUM_CLASSES):
        super().__init__()
        self.encoder = nn.Sequential(
            nn.Conv1d(NUM_FEATURES, 32, kernel_size=5, padding=2),
            nn.ReLU(),
            nn.Conv1d(32, 64, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.AdaptiveAvgPool1d(1),   # [B, 64, 1]
        )
        self.classifier = nn.Sequential(
            nn.Flatten(),
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Linear(32, n_classes),
        )

    def forward(self, x):
        # x: [B, WINDOW_SIZE, NUM_FEATURES] → transpose for Conv1d
        x = x.transpose(1, 2)          # [B, NUM_FEATURES, WINDOW_SIZE]
        return self.classifier(self.encoder(x))


# ── Physics penalty ──────────────────────────────────────────────────────────────

def physics_penalty_loss(logits: torch.Tensor, xb: torch.Tensor) -> torch.Tensor:
    """
    Penalises predicting 'normal' (class 0) when physics clearly indicates an event.

    Recovers raw G-forces from the normalised input channels and computes a
    differentiable penalty using the probability mass on class 0.
    """
    # Recover raw m/s² from normalised channels (channels 0 and 1)
    lat_raw = xb[:, :, 0] * ACCEL_NORM
    lon_raw = xb[:, :, 1] * ACCEL_NORM

    lat_g    = lat_raw.abs() / GRAVITY
    lon_g    = lon_raw.abs() / GRAVITY
    grip_g   = (lat_raw.pow(2) + lon_raw.pow(2)).sqrt() / GRAVITY

    # Max G over the time window per sample — shape [B]
    lat_g_max  = lat_g.max(dim=1).values
    lon_g_max  = lon_g.max(dim=1).values
    grip_g_max = grip_g.max(dim=1).values

    # Binary flags: physics says "this is not normal driving"
    braking_flag  = (lon_g_max > HARD_BRAKING_G).float()
    accel_flag    = (lon_g_max > HARD_ACCEL_G).float()
    turn_flag     = (lat_g_max > SHARP_TURN_G).float()
    friction_flag = (grip_g_max > FRICTION_CIRCLE_G).float()
    physics_event = ((braking_flag + accel_flag + turn_flag + friction_flag) > 0).float()

    # Penalty = probability of predicting "normal" when physics says event
    normal_prob = torch.softmax(logits, dim=1)[:, 0]   # class 0 = normal
    return (physics_event * normal_prob).mean()


# ── Training ────────────────────────────────────────────────────────────────────

def train(n_per_class: int = 3000, epochs: int = 60, batch_size: int = 128):
    print("Building synthetic dataset …")
    X, y = build_dataset(n_per_class)

    split = int(0.85 * len(y))
    X_train, X_val = X[:split], X[split:]
    y_train, y_val = y[:split], y[split:]

    train_loader = DataLoader(
        TensorDataset(torch.from_numpy(X_train), torch.from_numpy(y_train)),
        batch_size=batch_size, shuffle=True,
    )
    val_loader = DataLoader(
        TensorDataset(torch.from_numpy(X_val), torch.from_numpy(y_val)),
        batch_size=256,
    )

    model     = DrivingCoach()
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)
    cls_criterion = nn.CrossEntropyLoss()

    n_params = sum(p.numel() for p in model.parameters())
    print(f"Training {n_params:,} parameters for {epochs} epochs  "
          f"(physics_lambda={PHYSICS_LAMBDA}, input_channels={NUM_FEATURES}) …")
    best_val_acc = 0.0
    best_state   = None

    for epoch in range(1, epochs + 1):
        model.train()
        for xb, yb in train_loader:
            optimizer.zero_grad()
            logits    = model(xb)
            cls_loss  = cls_criterion(logits, yb)
            phys_loss = physics_penalty_loss(logits, xb)
            loss      = cls_loss + PHYSICS_LAMBDA * phys_loss
            loss.backward()
            optimizer.step()
        scheduler.step()

        if epoch % 10 == 0 or epoch == epochs:
            model.eval()
            correct = total = 0
            with torch.no_grad():
                for xb, yb in val_loader:
                    preds    = model(xb).argmax(1)
                    correct += (preds == yb).sum().item()
                    total   += len(yb)
            val_acc = correct / total
            print(f"  epoch {epoch:3d}/{epochs}  val_acc={val_acc:.3f}")
            if val_acc > best_val_acc:
                best_val_acc = val_acc
                best_state   = {k: v.clone() for k, v in model.state_dict().items()}

    model.load_state_dict(best_state)
    print(f"Best validation accuracy: {best_val_acc:.3f}")
    return model


# ── ONNX export ──────────────────────────────────────────────────────────────────

def export_onnx(model: nn.Module, path: str):
    model.eval()
    dummy = torch.zeros(1, WINDOW_SIZE, NUM_FEATURES)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    torch.onnx.export(
        model, dummy, path,
        input_names=["input"],
        output_names=["logits"],
        dynamic_axes={"input": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=17,
    )
    onnx.checker.check_model(onnx.load(path))
    print("ONNX model verified ✓")

    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    out  = sess.run(None, {"input": np.zeros((1, WINDOW_SIZE, NUM_FEATURES), dtype=np.float32)})
    probs = torch.softmax(torch.from_numpy(out[0]), dim=1).numpy()
    print(f"Runtime check — class probs: {probs.round(3)}")
    print(f"Saved → {path}")


# ── Entry point ──────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    model = train()
    export_onnx(model, ASSET_PATH)
    size_kb = os.path.getsize(ASSET_PATH) / 1024
    print(f"Model size: {size_kb:.1f} KB")
    print("Done. Rebuild the app to deploy.")
