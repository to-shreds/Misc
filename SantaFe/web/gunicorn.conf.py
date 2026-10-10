"""One process is required for the owner-wide command guard."""
import os

bind = "0.0.0.0:" + os.environ.get("PORT", "10000")
workers = 1
worker_class = "gthread"
threads = 4
timeout = 90
graceful_timeout = 40
accesslog = None
errorlog = "-"
capture_output = False
limit_request_line = 2048
limit_request_fields = 40
limit_request_field_size = 4096
