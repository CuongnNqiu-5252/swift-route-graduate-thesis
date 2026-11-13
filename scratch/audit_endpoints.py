import os
import re
from pathlib import Path

backend_dirs = [
    Path(r"c:\D\swiftroute\services\order-service\src\main\java"),
    Path(r"c:\D\swiftroute\services\driver-service\src\main\java"),
    Path(r"c:\D\swiftroute\services\auth-service\src\main\java"),
    Path(r"c:\D\swiftroute\services\notification-service\src\main\java"),
    Path(r"c:\D\swiftroute\services\routing-engine-service\api"),
]

web_dir = Path(r"c:\D\swiftroute\web-admin\src")
mobile_dir = Path(r"c:\D\swiftroute\mobile-app\lib")

# Read all frontend code
fe_contents = []
for p in web_dir.rglob("*.ts*"):
    try:
        fe_contents.append((str(p), p.read_text(encoding="utf-8", errors="ignore")))
    except Exception:
        pass

for p in mobile_dir.rglob("*.dart"):
    try:
        fe_contents.append((str(p), p.read_text(encoding="utf-8", errors="ignore")))
    except Exception:
        pass

# Also read backend files for internal service-to-service calls
backend_contents = []
for b_dir in backend_dirs:
    for p in b_dir.rglob("*.java"):
        try:
            backend_contents.append((str(p), p.read_text(encoding="utf-8", errors="ignore")))
        except Exception:
            pass
    for p in b_dir.rglob("*.py"):
        try:
            backend_contents.append((str(p), p.read_text(encoding="utf-8", errors="ignore")))
        except Exception:
            pass

endpoints = []

def parse_mapping_paths(val):
    # val could be "..." or '{"...", "..."}' or value = "..."
    # extract all strings inside quotes
    matches = re.findall(r'["\']([^"\']+)["\']', val)
    return matches if matches else [""]

# 1. Parse Java endpoints
for b_dir in backend_dirs:
    for f in b_dir.rglob("*Controller.java"):
        service_name = "order-service" if "order-service" in str(f) else \
                       "driver-service" if "driver-service" in str(f) else \
                       "auth-service" if "auth-service" in str(f) else \
                       "notification-service" if "notification-service" in str(f) else "other"
        content = f.read_text(encoding="utf-8", errors="ignore")
        
        # Class level prefix
        class_prefixes = [""]
        class_match = re.search(r'@RequestMapping\((.*?)\)\s*(?:@\w+.*?\s*)*public class', content, re.DOTALL)
        if class_match:
            raw_val = class_match.group(1)
            class_prefixes = parse_mapping_paths(raw_val)
            if not class_prefixes:
                class_prefixes = [""]

        # Method mappings
        method_pattern = r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\s*(?:\((.*?)\))?\s*(?:@\w+.*?\s*)*public\s+[\w\<\>\[\],\s]+\s+(\w+)\s*\('
        for m in re.finditer(method_pattern, content):
            http_method = m.group(1).replace("Mapping", "").upper()
            if http_method == "REQUEST":
                http_method = "ANY"
            mapping_arg = m.group(2) or '""'
            method_name = m.group(3)
            
            subpaths = parse_mapping_paths(mapping_arg)
            if not subpaths:
                subpaths = [""]

            for c_pref in class_prefixes:
                for sp in subpaths:
                    full_path = (c_pref.rstrip("/") + "/" + sp.lstrip("/")).strip()
                    if not full_path.startswith("/"):
                        full_path = "/" + full_path
                    if full_path.endswith("/") and len(full_path) > 1:
                        full_path = full_path.rstrip("/")
                    
                    endpoints.append({
                        "service": service_name,
                        "file": f.name,
                        "method": http_method,
                        "path": full_path,
                        "handler": method_name
                    })

# 2. Parse Python endpoints (routing-engine-service)
py_routes = Path(r"c:\D\swiftroute\services\routing-engine-service\api\routes.py")
if py_routes.exists():
    py_content = py_routes.read_text(encoding="utf-8", errors="ignore")
    # router = APIRouter(prefix="/api/v1") or similar
    router_prefix = "/api/v1"
    for m in re.finditer(r'@router\.(get|post|put|delete|patch)\(["\']([^"\']+)["\']', py_content):
        m_type = m.group(1).upper()
        p_path = router_prefix + m.group(2)
        endpoints.append({
            "service": "routing-engine-service",
            "file": "routes.py",
            "method": m_type,
            "path": p_path,
            "handler": "fastapi"
        })

print(f"Total endpoints parsed: {len(endpoints)}")

def search_in_codebase(path, codebase):
    # Paths in frontend might omit /api if axios has baseURL = .../api
    path_norm = path
    path_without_api = path.replace("/api", "") if path.startswith("/api") else path
    
    # Path with path variables replaced with regex or wildcard
    # e.g. /orders/{orderId}/status -> /orders/[^"'/]+/status
    # In JS/TS: `/orders/${orderId}/status` or `/orders/` + orderId + `/status`
    # or Dart: `/orders/$orderId/status`
    
    # Let's break path into static segments (ignore 'api' prefix since clients use baseURL with /api)
    segments = [s for s in path.split("/") if s and s != "api"]
    if not segments:
        return []

    static_segments = [s for s in segments if not s.startswith("{")]
    
    callers = []
    for filepath, filecontent in codebase:
        # Quick check: all static segments must appear in the file
        if all(seg in filecontent for seg in static_segments):
            for variant in [path, path_without_api]:
                parts = re.split(r'(\{[a-zA-Z0-9_]+\})', variant)
                escaped_parts = []
                for p in parts:
                    if p.startswith("{") and p.endswith("}"):
                        escaped_parts.append(r'(\$\{[^}]+\}|\$[a-zA-Z0-9_]+|[^"\'`\s/?#]+)')
                    else:
                        escaped_parts.append(re.escape(p))
                pattern_str = "".join(escaped_parts)
                try:
                    if re.search(pattern_str, filecontent):
                        callers.append(filepath)
                        break
                except Exception:
                    pass
    return callers

results = []
for ep in endpoints:
    fe_callers = search_in_codebase(ep["path"], fe_contents)
    be_callers = search_in_codebase(ep["path"], backend_contents)
    # Filter out the definition file itself from backend callers
    be_callers = [b for b in be_callers if ep["file"] not in b]
    
    results.append({
        **ep,
        "fe_callers": fe_callers,
        "be_callers": be_callers
    })

print(f"\n--- THỐNG KÊ KẾT QUẢ ---")
uncalled_by_fe = [r for r in results if not r["fe_callers"]]
print(f"Số lượng endpoint FE KHÔNG GỌI TRỰC TIẾP: {len(uncalled_by_fe)}")

for r in uncalled_by_fe:
    be_note = f"(Backend internal caller: {Path(r['be_callers'][0]).name})" if r["be_callers"] else "[CHƯA CÓ AI GỌI]"
    print(f"[{r['service']}] [{r['method']}] {r['path']} -> {r['file']}::{r['handler']} {be_note}")
