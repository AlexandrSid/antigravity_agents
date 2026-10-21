import os
import sys
import time
import json
import subprocess
import urllib.request
import urllib.error

PROJECT_NAME = "link-shortener-e2e"
BASE_URL = "http://localhost:8080"

def run_cmd(cmd):
    print(f"Executing: {' '.join(cmd)}")
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Command failed (code {res.returncode}):\n{res.stderr}")
    return res

def wait_for_ready(timeout_sec=60):
    start = time.time()
    print("Waiting for application container readiness...")
    while time.time() - start < timeout_sec:
        try:
            req = urllib.request.Request(f"{BASE_URL}/api/v1/links", headers={"Content-Type": "application/json"})
            # Sending empty POST or OPTIONS to check responsiveness
            try:
                urllib.request.urlopen(req, data=b"{}", timeout=2)
                return True
            except urllib.error.HTTPError as e:
                # 400 Bad Request means app is UP and validating requests
                if e.code in (400, 422, 200, 201):
                    print(f"Application is ready! (HTTP {e.code})")
                    return True
        except Exception:
            pass
        time.sleep(2)
    return False

def main():
    print(f"=== Starting E2E Docker Compose Run (project: {PROJECT_NAME}) ===")
    
    # 1. Clean previous state
    run_cmd(["docker", "compose", "-p", PROJECT_NAME, "down", "-v", "--remove-orphans"])
    
    success = False
    try:
        # 2. Build and launch full production stack
        up_res = run_cmd(["docker", "compose", "-p", PROJECT_NAME, "up", "-d", "--build", "--force-recreate"])
        if up_res.returncode != 0:
            sys.exit(1)
            
        # 3. Wait for readiness
        if not wait_for_ready():
            print("ERROR: Application failed to become ready within timeout.")
            run_cmd(["docker", "compose", "-p", PROJECT_NAME, "ps"])
            run_cmd(["docker", "compose", "-p", PROJECT_NAME, "logs"])
            sys.exit(1)

        # 4. Scenario: POST /api/v1/links
        target_url = "https://example.com/e2e-target-url"
        post_data = json.dumps({"url": target_url}).encode("utf-8")
        post_req = urllib.request.Request(
            f"{BASE_URL}/api/v1/links",
            data=post_data,
            headers={"Content-Type": "application/json"}
        )
        with urllib.request.urlopen(post_req, timeout=5) as resp:
            status = resp.status
            body = json.loads(resp.read().decode("utf-8"))
            print(f"POST Response: HTTP {status}, body={body}")
            assert status == 201, f"Expected 201, got {status}"
            assert "shortKey" in body, "Missing shortKey in response"
            short_key = body["shortKey"]

        # 5. Scenario: GET /{shortKey} (redirect check)
        class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
            def http_error_302(self, req, fp, code, msg, headers):
                return fp

        opener = urllib.request.build_opener(NoRedirectHandler)
        get_req = urllib.request.Request(f"{BASE_URL}/{short_key}")
        try:
            with opener.open(get_req, timeout=5) as get_resp:
                loc = get_resp.headers.get("Location")
                print(f"GET Response: Location={loc}")
                assert loc == target_url, f"Expected Location {target_url}, got {loc}"
        except urllib.error.HTTPError as e:
            if e.code == 302:
                loc = e.headers.get("Location")
                print(f"GET 302 Response: Location={loc}")
                assert loc == target_url, f"Expected Location {target_url}, got {loc}"
            else:
                raise

        # 6. Scenario: 404 on unknown key
        try:
            opener.open(f"{BASE_URL}/nonexist99", timeout=5)
            assert False, "Expected 404"
        except urllib.error.HTTPError as e:
            print(f"GET unknown key: HTTP {e.code}")
            assert e.code == 404, f"Expected 404, got {e.code}"

        print("=== ALL E2E DOCKER COMPOSE SCENARIOS PASSED ===")
        success = True

    finally:
        # 7. Always clean in finally
        print("Cleaning up Docker Compose containers and volumes...")
        run_cmd(["docker", "compose", "-p", PROJECT_NAME, "down", "-v", "--remove-orphans"])

    if not success:
        sys.exit(1)

if __name__ == "__main__":
    main()
