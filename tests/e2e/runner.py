"""
Standalone CLI test runner for SwiftRoute E2E test suite.
Supports tier filtering, mode selection, summary tables, and strict exit code conventions:
  0: All tests passed cleanly
  1: One or more tests failed
  2: Runtime / execution error
"""

import argparse
import os
import sys
import time
from pathlib import Path
from typing import List, Dict, Any

# Ensure project root is in sys.path for direct script execution
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import pytest


class ResultCollector:
    """Pytest plugin collecting structured test execution results."""

    def __init__(self):
        self.reports: List[Any] = []
        self.passed: List[Any] = []
        self.failed: List[Any] = []
        self.skipped: List[Any] = []

    def pytest_runtest_logreport(self, report):
        if report.when == "call":
            self.reports.append(report)
            if report.passed:
                self.passed.append(report)
            elif report.failed:
                self.failed.append(report)
            elif report.skipped:
                self.skipped.append(report)
        elif report.failed and report.when in ("setup", "teardown"):
            # Setup or teardown failure
            self.reports.append(report)
            self.failed.append(report)


def print_summary_table(collector: ResultCollector, duration: float, tier: str, mode: str):
    """Format and print an executive summary table of test results."""
    total = len(collector.reports)
    passed = len(collector.passed)
    failed = len(collector.failed)
    skipped = len(collector.skipped)
    pass_pct = (passed / total * 100.0) if total > 0 else 0.0

    sep = "=" * 80
    print("\n" + sep)
    print(f" SWIFTROUTE SECURITY HARDENING E2E TEST RUNNER")
    print(f" Execution Mode: {mode.upper()} | Selected Tier: {tier.upper()} | Duration: {duration:.2f}s")
    print(sep)
    print(f" {'TEST RESULT SUMMARY':<30} {'COUNT':<10} {'PERCENTAGE':<10}")
    print("-" * 80)
    print(f" {'Total Executed':<30} {total:<10} {'100.0%':<10}")
    print(f" {'Passed':<30} {passed:<10} {f'{pass_pct:.1f}%':<10}")
    print(f" {'Failed':<30} {failed:<10} {f'{(failed / total * 100.0 if total else 0):.1f}%':<10}")
    print(f" {'Skipped':<30} {skipped:<10} {f'{(skipped / total * 100.0 if total else 0):.1f}%':<10}")
    print("-" * 80)

    if failed > 0:
        print("\nFAILED TESTS DETAILS:")
        for r in collector.failed:
            print(f"  [X] {r.nodeid}")
            if hasattr(r, "longreprtext"):
                # Print first few lines of error
                lines = r.longreprtext.splitlines()[:5]
                for line in lines:
                    print(f"      {line}")
        print("-" * 80)

    status_str = "SUCCESS" if failed == 0 and total > 0 else ("NO TESTS" if total == 0 else "FAILURE")
    print(f" OVERALL STATUS: {status_str}")
    print(sep + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="SwiftRoute E2E Security Hardening Test Runner"
    )
    parser.add_argument(
        "--tier",
        choices=["1", "2", "3", "4", "all"],
        default="1",
        help="Test tier to execute: 1 (feature coverage), 2 (boundary), 3 (combinations), 4 (scenarios), or all (default: 1)",
    )
    parser.add_argument(
        "--mode",
        choices=["auto", "live", "mock"],
        default="auto",
        help="Test execution mode: auto, live, or mock (default: auto)",
    )
    parser.add_argument(
        "-v", "--verbose", action="store_true", help="Enable verbose pytest output"
    )
    parser.add_argument(
        "-k", "--keyword", type=str, default=None, help="Pytest keyword expression filter"
    )

    try:
        args = parser.parse_args()
    except SystemExit:
        return 2
    except Exception as e:
        print(f"Error parsing CLI arguments: {e}", file=sys.stderr)
        return 2

    # Set execution mode in environment
    os.environ["SWIFTROUTE_TEST_MODE"] = args.mode

    # Resolve target directory based on tier
    tier_mapping = {
        "1": "tests/e2e/tier1_coverage",
        "2": "tests/e2e/tier2_boundary",
        "3": "tests/e2e/tier3_combinations",
        "4": "tests/e2e/tier4_scenarios",
        "all": "tests/e2e",
    }
    target_path = tier_mapping[args.tier]

    pytest_args = [target_path]
    if args.verbose:
        pytest_args.append("-v")
    else:
        pytest_args.append("-q")

    if args.keyword:
        pytest_args.extend(["-k", args.keyword])

    collector = ResultCollector()
    start_time = time.time()

    try:
        exit_code = pytest.main(pytest_args, plugins=[collector])
        duration = time.time() - start_time
    except Exception as e:
        print(f"Fatal error during pytest execution: {e}", file=sys.stderr)
        return 2

    print_summary_table(collector, duration, tier=args.tier, mode=args.mode)

    # Standardized Exit Codes:
    # 0 = All passed
    # 1 = One or more failed
    # 2 = Runtime error or invalid arguments
    if len(collector.failed) > 0:
        return 1
    elif exit_code not in (pytest.ExitCode.OK, pytest.ExitCode.NO_TESTS_COLLECTED):
        return 2 if exit_code == pytest.ExitCode.USAGE_ERROR else 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
