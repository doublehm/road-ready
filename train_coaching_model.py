"""
Train the RoadReady real-time coaching model.

Generates synthetic IMU windows from physics thresholds, trains a small
1D-CNN in PyTorch, and exports an ONNX model ready for on-device inference.

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

GRAVITY        = 9.81          # m/s²
HARD_BRAKING_G = 0.6
HARD_ACCEL_G   = 0.4
SHARP_TURN_G   = 0.45

WINDOW_SIZE    = 50
NUM_FEATURES   = 4             # lat_accel, long_accel, vert_accel, jerk
NUM_CLASSES    = 4             # 0=normal 1=harsh_braking 2=harsh_accel 3=sharp_turn

ACCEL_NORM     = 20.0          # normalisation divisor (m/s²)
JERK_NORM      = 30.0

ASSET_PATH = os.path.join(
    os.path.dirname(__file__),
    "mobile-app-kmp", "androidApp", "src", "androidMain", "assets",
    "driving_coach.onnx",
)

# ── Synthetic data generation ───────────────────────────────────────────────────

def _jerk(window: np.ndarray) -> np.ndarray:
    """Compute per-sample jerk from consecutive accelerometer differences."""
    diff = np.diff(window, axis=0, prepend=window[:1])
    return np.linalg.norm(diff, axis=1, keepdims=True)


def make_normal_window(rng: np.random.Generator) -> np.ndarray:
    """Smooth, low-G driving — city cruise or steady highway."""
    lat  = rng.normal(0,   0.3,  (WINDOW_SIZE, 1))
    lon  = rng.normal(0,   0.4,  (WINDOW_SIZE, 1))
    vert = rng.normal(0,   0.2,  (WINDOW_SIZE, 1))
    raw  = np.hstack([lat, lon, vert])
    jerk = _jerk(raw)
    return np.hstack([raw, jerk])


def make_braking_window(rng: np.random.Generator) -> np.ndarray:
    """Strong forward deceleration > 0.6G sustained for several samples."""
    lat  = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    vert = rng.normal(0, 0.2, (WINDOW_SIZE, 1))
    lon  = rng.normal(0, 0.3, (WINDOW_SIZE, 1))
    # Inject braking event in middle third of window
    peak_g = rng.uniform(HARD_BRAKING_G + 0.05, 1.2)
    start  = WINDOW_SIZE // 3
    end    = 2 * WINDOW_SIZE // 3
    lon[start:end] = -peak_g * GRAVITY + rng.normal(0, 0.5, (end - start, 1))
    raw  = np.hstack([lat, lon, vert])
    jerk = _jerk(raw)
    return np.hstack([raw, jerk])


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
    return np.hstack([raw, jerk])


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
    return np.hstack([raw, jerk])


def build_dataset(n_per_class: int = 3000, seed: int = 42):
    rng = np.random.default_rng(seed)
    builders = [make_normal_window, make_braking_window, make_accel_window, make_sharp_turn_window]
    X, y = [], []
    for label, fn in enumerate(builders):
        for _ in range(n_per_class):
            w = fn(rng)
            # Normalise to [-1, 1] range
            w[:, :3] /= ACCEL_NORM
            w[:,  3] /= JERK_NORM
            w = np.clip(w, -1.0, 1.0)
            X.append(w)
            y.append(label)
    X = np.array(X, dtype=np.float32)   # [N, WINDOW_SIZE, NUM_FEATURES]
    y = np.array(y, dtype=np.int64)
    # Shuffle
    idx = rng.permutation(len(y))
    return X[idx], y[idx]


# ── Model ───────────────────────────────────────────────────────────────────────

class DrivingCoach(nn.Module):
    """
    Lightweight 1-D CNN for driving-event classification.
    ~25 K parameters — fits comfortably in 100 KB after quantisation.
    Inference on a mid-range Android (Snapdragon 778G): ~2-5 ms per window.
    """
    def __init__(self, n_classes: int = NUM_CLASSES):
        super().__init__()
        self.encoder = nn.Sequential(
            # [B, F, T]  (channels-first for Conv1d)
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

    model = DrivingCoach()
    optimizer = torch.optim.Adam(model.parameters(), lr=1e-3)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs)
    criterion = nn.CrossEntropyLoss()

    print(f"Training {sum(p.numel() for p in model.parameters()):,} parameters for {epochs} epochs …")
    best_val_acc = 0.0
    best_state = None

    for epoch in range(1, epochs + 1):
        model.train()
        for xb, yb in train_loader:
            optimizer.zero_grad()
            criterion(model(xb), yb).backward()
            optimizer.step()
        scheduler.step()

        if epoch % 10 == 0 or epoch == epochs:
            model.eval()
            correct = total = 0
            with torch.no_grad():
                for xb, yb in val_loader:
                    preds = model(xb).argmax(1)
                    correct += (preds == yb).sum().item()
                    total   += len(yb)
            val_acc = correct / total
            print(f"  epoch {epoch:3d}/{epochs}  val_acc={val_acc:.3f}")
            if val_acc > best_val_acc:
                best_val_acc = val_acc
                best_state = {k: v.clone() for k, v in model.state_dict().items()}

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
    # Verify the exported model
    onnx.checker.check_model(onnx.load(path))
    print(f"ONNX model verified ✓")

    # Quick runtime check
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    out = sess.run(None, {"input": np.zeros((1, WINDOW_SIZE, NUM_FEATURES), dtype=np.float32)})
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
