#!/usr/bin/env node
import { spawn } from "child_process";

const server = spawn("node", ["index.js"], {
  stdio: ["pipe", "pipe", "pipe"],
  cwd: "/Users/dr.youd/Desktop/Atexo/ask2Watch/ask2watch-mcp-tmdb",
});

let requestId = 1;

function sendRequest(method, params = {}) {
  const message = {
    jsonrpc: "2.0",
    id: requestId++,
    method,
    params,
  };
  const json = JSON.stringify(message) + "\n";
  console.log(`\n→ Sending: ${json.trim()}`);
  server.stdin.write(json);
}

// Collect responses
let buffer = "";
server.stdout.on("data", (data) => {
  buffer += data.toString();
  const lines = buffer.split("\n");
  buffer = lines.pop(); // Keep incomplete line in buffer

  for (const line of lines) {
    if (line.trim()) {
      // Only process valid JSON-RPC responses
      if (line.trim().startsWith("{")) {
        try {
          console.log(`← Response: ${line}`);
          const response = JSON.parse(line);
          if (response.id === 1) {
            // After initialize, send initialized
            setTimeout(() => sendRequest("initialized"), 500);
          } else if (response.id === 2) {
            // After initialized, list tools
            setTimeout(() => sendRequest("tools/list"), 500);
          } else if (response.id === 3) {
            // After list tools, call a tool
            setTimeout(
              () =>
                sendRequest("tools/call", {
                  name: "search_movie",
                  arguments: { query: "Inception" },
                }),
              500
            );
          } else if (response.id === 4) {
            console.log("\n✅ MCP Server works!");
            process.exit(0);
          }
        } catch (e) {
          console.log(`← (Ignored non-JSON): ${line}`);
        }
      } else {
        console.log(`← (Debug): ${line}`);
      }
    }
  }
});

server.stderr.on("data", (data) => {
  console.error(`[stderr] ${data}`);
});

server.on("close", (code) => {
  console.log(`\nServer closed with code ${code}`);
  process.exit(code);
});

// Start the handshake
console.log("Starting MCP handshake...");
sendRequest("initialize", {
  protocolVersion: "2024-11-05",
  capabilities: {},
  clientInfo: {
    name: "test-client",
    version: "1.0.0",
  },
});

// Timeout after 10 seconds
setTimeout(() => {
  console.error("\n❌ Timeout!");
  process.exit(1);
}, 10000);
