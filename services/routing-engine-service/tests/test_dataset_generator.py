import os
import pandas as pd
import pytest
from core.dataset_generator import generate_training_dataset

def test_generate_small_dataset(tmp_path):
    output_parquet = str(tmp_path / "test_dataset.parquet")

    # Generate small dataset: 5 scenarios, 8 stops
    df = generate_training_dataset(
        num_scenarios=5,
        min_stops=8,
        max_stops=8,
        output_parquet_path=output_parquet,
        seed=100,
    )

    assert isinstance(df, pd.DataFrame)
    assert len(df) > 0
    assert os.path.exists(output_parquet)

    # Check required columns
    expected_cols = [
        "dist_uv_norm",
        "dist_v_depot_norm",
        "dist_rank_norm",
        "unvisited_ratio",
        "time_slack_norm",
        "demand_ratio",
        "angle_to_depot",
        "is_optimal",
    ]
    for col in expected_cols:
        assert col in df.columns

    # Verify target label contains both 0 and 1
    assert set(df["is_optimal"].unique()) == {0, 1}

    # Verify Parquet file can be read back identically
    df_read = pd.read_parquet(output_parquet)
    assert len(df_read) == len(df)
    assert list(df_read.columns) == list(df.columns)
