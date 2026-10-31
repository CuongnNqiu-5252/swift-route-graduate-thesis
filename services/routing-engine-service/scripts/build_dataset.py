import os
import sys

# Ensure routing-engine-service root is on sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from core.dataset_generator import generate_training_dataset

def main():
    output_dir = "data"
    os.makedirs(output_dir, exist_ok=True)
    parquet_path = os.path.join(output_dir, "dataset_v1.parquet")

    print(f"Generating training dataset with Held-Karp exact solver...")
    df = generate_training_dataset(
        num_scenarios=80,
        min_stops=8,
        max_stops=11,
        output_parquet_path=parquet_path,
        seed=42,
    )
    opt_ratio = df["is_optimal"].mean() * 100.0
    print(f"SUCCESS: Generated {len(df):,} transition samples.")
    print(f"Optimal transitions: {opt_ratio:.2f}% (positive class)")
    print(f"Saved to: {os.path.abspath(parquet_path)}")

if __name__ == "__main__":
    main()
