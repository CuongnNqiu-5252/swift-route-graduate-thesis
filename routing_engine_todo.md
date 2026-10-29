# CHECKLIST TRIỂN KHAI: `routing-engine-service`

> Bạn sẽ code theo hướng dẫn này, AI sẽ review sau từng giai đoạn.  
> Tất cả file Python cần dùng **venv đã có sẵn** tại `services/routing-engine-service/venv/`.

---

## ⚙️ SETUP (Chỉ làm 1 lần)

### 1. Kích hoạt venv và kiểm tra thư viện

Mở terminal tại thư mục `services/routing-engine-service/`:

```powershell
# Kích hoạt virtual environment
.\venv\Scripts\activate

# Kiểm tra các thư viện cần thiết đã có chưa
pip list | Select-String "fastapi|numpy|scikit-learn|torch|pandas|scipy|lightgbm"
```

> ⚠️ Nếu **thiếu `lightgbm`** (dùng cho ML Pruner, giai đoạn 3), cài thêm:
> ```powershell
> pip install lightgbm
> ```

### 2. Tạo cấu trúc thư mục

Chạy lệnh sau để tạo toàn bộ skeleton (tạo file trống trước, điền nội dung sau):

```powershell
# Đứng tại services/routing-engine-service/
New-Item -ItemType Directory -Force -Path api, core, service, models, tests

New-Item -ItemType File -Force -Path `
  api/__init__.py, `
  api/routes.py, `
  api/schemas.py, `
  core/__init__.py, `
  core/distance.py, `
  core/clustering.py, `
  core/tsp_nearest_neighbor.py, `
  core/tsp_genetic.py, `
  core/tsp_held_karp.py, `
  core/ml_pruner.py, `
  core/dynamic_insert.py, `
  service/__init__.py, `
  service/optimizer_service.py, `
  tests/__init__.py, `
  tests/test_distance.py, `
  tests/test_clustering.py, `
  tests/test_held_karp.py, `
  tests/test_baselines.py, `
  tests/test_benchmark.py, `
  main.py, `
  .env.example
```

### 3. Tạo `main.py` (Entry point FastAPI)

```python
# main.py
from fastapi import FastAPI
from api.routes import router

app = FastAPI(title="SwiftRoute Routing Engine", version="1.0.0")
app.include_router(router, prefix="/api/v1")

@app.get("/health")
def health():
    return {"status": "ok"}
```

---

## 📁 CẤU TRÚC THƯ MỤC CHUẨN

```
services/routing-engine-service/
├── api/
│   ├── __init__.py
│   ├── routes.py           # POST /optimize/batch, POST /optimize/insert
│   └── schemas.py          # Pydantic models: Request, Response
├── core/                   # THUẦN PYTHON — không import FastAPI
│   ├── __init__.py
│   ├── distance.py         # Haversine + ma trận khoảng cách/thời gian
│   ├── clustering.py       # Capacitated K-Means / Sweep
│   ├── tsp_nearest_neighbor.py   # Baseline 1
│   ├── tsp_genetic.py      # Baseline 2
│   ├── tsp_held_karp.py    # Exact DP (Bitmask Held-Karp)
│   ├── ml_pruner.py        # ML Top-K Pruning tích hợp vào DP
│   └── dynamic_insert.py   # Cheapest Insertion cho đơn khẩn cấp
├── service/
│   ├── __init__.py
│   └── optimizer_service.py  # Điều phối: nhận request → gọi core → trả kết quả
├── models/
│   └── pruner_model.pkl    # (Giai đoạn 3) Trọng số model đã train
├── tests/
│   ├── __init__.py
│   ├── test_distance.py
│   ├── test_clustering.py
│   ├── test_held_karp.py
│   ├── test_baselines.py
│   └── test_benchmark.py   # Benchmark 4 thuật toán, xuất CSV + biểu đồ
├── main.py
├── .env.example
└── requirements.txt        # (đã có)
```

---

## ✅ GIAI ĐOẠN 1 — Nền tảng khoảng cách & 2 Baselines

> **Mục tiêu:** Có đủ công cụ để đo lường và so sánh thuật toán.

### File 1: `core/distance.py`

**Yêu cầu bạn implement:**

```python
# Hàm 1: Khoảng cách Haversine giữa 2 tọa độ
def haversine(lat1, lng1, lat2, lng2) -> float:
    """Trả về khoảng cách (km) giữa 2 điểm GPS."""
    ...

# Hàm 2: Ma trận khoảng cách NxN
def build_distance_matrix(locations: list[tuple[float, float]]) -> list[list[float]]:
    """locations = [(lat, lng), ...]. Trả về ma trận dist[i][j] tính bằng km."""
    ...

# Hàm 3: Ma trận thời gian di chuyển NxN
def build_time_matrix(dist_matrix: list[list[float]], avg_speed_kmh: float = 28.0) -> list[list[float]]:
    """Trả về ma trận time[i][j] tính bằng phút."""
    ...
```

**Gợi ý:** Dùng công thức Haversine chuẩn với `math.radians`, `math.sin`, `math.cos`, `math.asin`, `math.sqrt`. Bán kính Trái Đất R = 6371 km.

---

### File 2: `core/tsp_nearest_neighbor.py`

**Yêu cầu bạn implement:**

```python
def nearest_neighbor_tsp(
    dist_matrix: list[list[float]],
    start: int = 0
) -> tuple[list[int], float]:
    """
    Thuật toán Nearest Neighbor (Greedy Heuristic).
    
    Args:
        dist_matrix: Ma trận khoảng cách NxN.
        start: Index điểm xuất phát (mặc định là 0 = kho).
    
    Returns:
        (route, total_distance)
        route: Danh sách index theo thứ tự thăm, bắt đầu và kết thúc tại `start`.
        total_distance: Tổng quãng đường (km).
    
    Complexity: O(n^2)
    """
    ...
```

---

### File 3: `core/tsp_held_karp.py`

**Yêu cầu bạn implement (quan trọng nhất giai đoạn 1-2):**

```python
def held_karp_tsp(
    dist_matrix: list[list[float]]
) -> tuple[list[int], float]:
    """
    Thuật toán Held-Karp Bitmask DP (Exact TSP Solver).
    
    Args:
        dist_matrix: Ma trận khoảng cách NxN. Node 0 = kho xuất phát.
    
    Returns:
        (route, total_distance)
        route: Thứ tự tối ưu, bắt đầu và kết thúc tại node 0.
        total_distance: Tổng quãng đường tối ưu tuyệt đối (km).
    
    Complexity: O(n^2 * 2^n) — chỉ dùng cho n <= 15.
    """
    ...
```

**Gợi ý logic:**
1. Khởi tạo `dp[mask][u]` = cost nhỏ nhất để đi từ node 0, qua tập `mask`, đang đứng ở `u`.
2. Base case: `dp[1 << i][i] = dist[0][i]` với mọi i (xuất phát từ kho đến i).
3. Transition: `dp[mask][u] = min(dp[mask ^ (1<<u)][v] + dist[v][u])` với mọi v trong `mask`.
4. Kết quả: `min(dp[full_mask][u] + dist[u][0])` với mọi u.
5. Dùng mảng `parent[mask][u]` để truy vết lộ trình.

---

### File 4: `tests/test_distance.py` và `tests/test_held_karp.py`

**Bạn cần viết test tối thiểu:**

```python
# test_distance.py — kiểm tra Haversine
def test_haversine_hanoi_hcm():
    # Hà Nội: (21.0285, 105.8542), HCM: (10.8231, 106.6297)
    # Khoảng cách thực tế ~1150 km, chấp nhận sai số 5%
    dist = haversine(21.0285, 105.8542, 10.8231, 106.6297)
    assert 1100 <= dist <= 1200

# test_held_karp.py — kiểm tra tính đúng đắn bằng Brute-force
def test_held_karp_vs_bruteforce_n5():
    # Tạo 5 điểm ngẫu nhiên (kể cả kho), so sánh kết quả held-karp vs itertools.permutations
    ...
```

---

## ✅ GIAI ĐOẠN 2 — `tsp_genetic.py` & Sinh dataset

> Làm sau khi Giai đoạn 1 đã pass test.

### File 5: `core/tsp_genetic.py`

**Yêu cầu bạn implement:**

```python
def genetic_tsp(
    dist_matrix: list[list[float]],
    population_size: int = 50,
    generations: int = 200,
    mutation_rate: float = 0.02
) -> tuple[list[int], float]:
    """
    Thuật toán Di truyền (Genetic Algorithm) cho TSP.
    
    - Mã hóa: hoán vị các node (trừ kho node 0).
    - Toán tử lai ghép: Ordered Crossover (OX).
    - Toán tử đột biến: Swap Mutation.
    - Selection: Tournament selection.
    
    Returns: (route, total_distance) — route bắt đầu và kết thúc tại node 0.
    """
    ...
```

### Sinh Dataset (chạy script, không phải code thư viện)

> Sau khi Held-Karp hoạt động đúng, chạy script này để sinh 10,000 samples:

```powershell
# Đứng tại services/routing-engine-service/
python -c "
from tests.test_benchmark import generate_training_dataset
generate_training_dataset(n_scenarios=1000, n_range=(8, 14), output='../ml-training-service/data/dataset_transitions.csv')
"
```

---

## ✅ GIAI ĐOẠN 3 — ML Pruner

> Làm sau khi có dataset.

### File 6: `core/ml_pruner.py`

Sẽ hướng dẫn chi tiết khi đến giai đoạn này.  
Tóm tắt: Tải model LightGBM từ `models/pruner_model.pkl`, nhận features của cạnh `(u, v, mask)`, trả về Top-K node được dự đoán là nằm trong hành trình tối ưu.

---

## ✅ GIAI ĐOẠN 4 — Clustering & Dynamic Insert & API

> Làm sau khi ML Pruner hoạt động.

### File 7: `core/clustering.py`
### File 8: `core/dynamic_insert.py`  
### File 9: `api/schemas.py` + `api/routes.py` + `service/optimizer_service.py`

---

## 🚀 CHẠY THỬ SERVER (bất kỳ lúc nào để kiểm tra)

```powershell
# Đứng tại services/routing-engine-service/
.\venv\Scripts\activate
uvicorn main:app --reload --port 8083
```

Sau đó truy cập: `http://localhost:8083/docs` để xem Swagger UI.

---

## 📋 THỨ TỰ NÊN LÀM

| Thứ tự | Việc cần làm | File đích |
|--------|-------------|-----------|
| 1 | Setup cấu trúc thư mục (chạy lệnh ở trên) | — |
| 2 | Tạo `main.py` | `main.py` |
| 3 | Implement `haversine` + `build_distance_matrix` + `build_time_matrix` | `core/distance.py` |
| 4 | Implement `nearest_neighbor_tsp` | `core/tsp_nearest_neighbor.py` |
| 5 | Implement `held_karp_tsp` | `core/tsp_held_karp.py` |
| 6 | Viết test cho distance + held_karp | `tests/test_distance.py`, `tests/test_held_karp.py` |
| 7 | Chạy `pytest tests/test_distance.py tests/test_held_karp.py` và pass hết | — |
| 8 | Implement `genetic_tsp` | `core/tsp_genetic.py` |
| 9 | Viết test benchmark sơ bộ (so sánh 3 thuật toán, n=10) | `tests/test_baselines.py` |
| 10 | **Gửi cho AI review** ✅ | — |
