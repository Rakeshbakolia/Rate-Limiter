import urllib.request
import urllib.error
import json
import uuid
import time

def send_request(url, client_id, method="GET"):
    req = urllib.request.Request(url, method=method)
    req.add_header("X-Client-ID", client_id)
    try:
        with urllib.request.urlopen(req) as response:
            status = response.status
            headers = dict(response.info())
            body = response.read().decode('utf-8')
            return status, headers, body
    except urllib.error.HTTPError as e:
        status = e.code
        headers = dict(e.info())
        body = e.read().decode('utf-8')
        return status, headers, body
    except Exception as e:
        return 500, {}, str(e)

def main():
    client_id = f"load-test-client-{uuid.uuid4().hex[:8]}"
    resource_url = "http://localhost:8080/api/v1/resource"
    admin_url = f"http://localhost:8080/api/v1/admin/ratelimit/{client_id}"
    metrics_url = "http://localhost:8080/actuator/prometheus"

    print("==================================================")
    print(f"Starting Load Test for client: {client_id}")
    print("==================================================")

    # 1. Send 11 sequential requests (capacity is 10)
    for i in range(1, 12):
        print(f"\n--- Request #{i} ---")
        status, headers, body = send_request(resource_url, client_id)
        print(f"HTTP Status: {status}")
        if "X-RateLimit-Remaining" in headers:
            print(f"X-RateLimit-Remaining: {headers['X-RateLimit-Remaining']}")
        if "Retry-After" in headers:
            print(f"Retry-After: {headers['Retry-After']} seconds")
        print(f"Response Body: {body}")
        time.sleep(0.05) # Small sleep between requests

    print("\n==================================================")
    print("Triggering Admin Reset API to clear lockout...")
    print("==================================================")
    
    # 2. Call admin delete endpoint
    reset_status, reset_headers, reset_body = send_request(admin_url, client_id, method="DELETE")
    print(f"Admin Delete Status: {reset_status}")
    print(f"Admin Delete Response: {reset_body}")

    print("\n==================================================")
    print("Verifying immediate access recovery after reset...")
    print("==================================================")

    # 3. Verify request is allowed again
    post_reset_status, post_reset_headers, post_reset_body = send_request(resource_url, client_id)
    print(f"HTTP Status: {post_reset_status}")
    if "X-RateLimit-Remaining" in post_reset_headers:
        print(f"X-RateLimit-Remaining: {post_reset_headers['X-RateLimit-Remaining']}")
    print(f"Response Body: {post_reset_body}")

    print("\n==================================================")
    print("Fetching Custom Prometheus Metrics...")
    print("==================================================")

    # 4. Fetch metrics and filter for rate limiting metrics
    m_status, m_headers, m_body = send_request(metrics_url, "")
    if m_status == 200:
        lines = m_body.split("\n")
        ratelimit_metrics = [line for line in lines if "ratelimit" in line]
        for metric in ratelimit_metrics:
            print(metric)
    else:
        print("Could not fetch metrics.")

if __name__ == "__main__":
    main()
