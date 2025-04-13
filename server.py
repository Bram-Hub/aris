import http.server
import socketserver
import os

PORT = 8000  # Change this if needed
DIRECTORY = "web-app/"

# Use absolute path to prevent chdir issues
BASE_DIR = os.path.abspath(DIRECTORY)

class CustomHandler(http.server.SimpleHTTPRequestHandler):
    def translate_path(self, path):
        """Serve files from the correct static directory."""
        path = super().translate_path(path)
        rel_path = os.path.relpath(path, os.getcwd())
        return os.path.join(BASE_DIR, rel_path)

# Ensure correct MIME type for JavaScript files
CustomHandler.extensions_map.update({
    ".js": "application/javascript",
    ".mjs": "application/javascript",
})

# Start server
try:
    httpd = socketserver.TCPServer(("127.0.0.1", PORT), CustomHandler)
    print(f"Serving at http://localhost:{PORT}")
    httpd.serve_forever()
except KeyboardInterrupt:
    print("\nServer shutting down...")
    httpd.server_close()


