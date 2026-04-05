import joblib
import pandas as pd
import numpy as np
import os
from typing import List, Dict, Any

class MLEvaluator:
    """
    ML-based evaluator for driving mistakes.
    Uses a trained RandomForest model to detect mistakes in sensor windows.
    """
    def __init__(self, model_path: str = "app/models/driving_mistake_model.joblib", threshold: float = 0.7):
        self.model_path = model_path
        self.model = None
        self.threshold = threshold
        if os.path.exists(model_path):
            try:
                self.model = joblib.load(model_path)
            except Exception as e:
                pass # Silently fail or log to real logger
                
        self.label_map = {
            0: "Normal",
            1: "Harsh Braking",
            2: "Sharp Turn",
            3: "Sudden Stop"
        }

    def predict_window(self, window_data: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Takes a window of accelerometer data (30 samples) and returns a prediction.
        """
        if self.model is None:
            return {"type": "Normal", "confidence": 0.0, "label_id": 0}
            
        df = pd.DataFrame(window_data)
        
        # Calculate jerk and magnitude as expected by the model
        df['dt'] = df['timestamp'].diff() / 1000.0
        df['dt'] = df['dt'].replace(0, float('nan'))
        df['dx'] = df['x'].diff() / df['dt']
        df['dy'] = df['y'].diff() / df['dt']
        df['dz'] = df['z'].diff() / df['dt']
        df['jerk'] = (df['dx']**2 + df['dy']**2 + df['dz']**2)**0.5
        df['mag'] = (df['x']**2 + df['y']**2 + df['z']**2)**0.5
        df = df.replace([float('inf'), float('-inf')], float('nan')).fillna(0)
        
        # Extract features
        features = {
            'mean_x': df['x'].mean(), 'mean_y': df['y'].mean(), 'mean_z': df['z'].mean(),
            'std_x': df['x'].std(), 'std_y': df['y'].std(), 'std_z': df['z'].std(),
            'max_x': df['x'].max(), 'max_y': df['y'].max(), 'max_z': df['z'].max(),
            'min_x': df['x'].min(), 'min_y': df['y'].min(), 'min_z': df['z'].min(),
            'jerk_mean': df['jerk'].mean(), 'jerk_std': df['jerk'].std(),
            'mag_mean': df['mag'].mean(), 'mag_std': df['mag'].std(),
            'mag_max': df['mag'].max()
        }
        
        # Convert to DataFrame with same column order as training
        X = pd.DataFrame([features])
        
        # Prediction
        label_id = self.model.predict(X)[0]
        probs = self.model.predict_proba(X)[0]
        
        # Find index of predicted label in model.classes_
        label_idx = list(self.model.classes_).index(label_id)
        confidence = probs[label_idx]
        
        return {
            "type": self.label_map.get(label_id, "Unknown"),
            "confidence": float(confidence),
            "label_id": int(label_id)
        }

    def evaluate_ride(self, acceleration_data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """
        Runs ML model across the entire ride in sliding windows.
        Returns a list of ML-detected events.
        """
        if self.model is None or not acceleration_data:
            return []
            
        WINDOW_SIZE = 30
        STEP_SIZE = 10
        
        ml_events = []
        
        for i in range(0, len(acceleration_data) - WINDOW_SIZE, STEP_SIZE):
            window = acceleration_data[i : i + WINDOW_SIZE]
            prediction = self.predict_window(window)
            
            if prediction['label_id'] != 0 and prediction['confidence'] > self.threshold:
                # Flag this as an ML event
                ml_events.append({
                    'type': f"ml_{prediction['type'].lower().replace(' ', '_')}",
                    'timestamp': window[-1]['timestamp'],
                    'confidence': prediction['confidence'],
                    'lat': window[-1].get('latitude'),
                    'lng': window[-1].get('longitude'),
                    'description': f"ML detected: {prediction['type']} (Confidence: {prediction['confidence']:.2f})"
                })
                
        return ml_events
