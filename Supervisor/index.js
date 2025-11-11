import dotenv from "dotenv";
dotenv.config({ path: process.env.NODE_ENV === "production" ? "/etc/msu/.env" : "./.env" });

import express from "express";
import crypto from "crypto";
import axios from "axios";

const app = express();
app.use(express.json());

// --- Secret (shared with Auth Server only) ---
const AUTH_SECRET = process.env.AUTH_SECRET || "dev-super-secret"; 

// Utility: simple auth header check
function verifyAuth(req, res, next) {
  const signature = req.headers["x-signature"];
  const timestamp = req.headers["x-timestamp"];
  if (!signature || !timestamp)
    return res.status(400).json({ ok: false, error: "missing signature" });

  const body = JSON.stringify(req.body);
  const expected = crypto
    .createHmac("sha256", AUTH_SECRET)
    .update(timestamp + ":" + body)
    .digest("hex");

  if (signature !== expected)
    return res.status(403).json({ ok: false, error: "invalid signature" });

  // optional: check timestamp freshness
  if (Math.abs(Date.now() - Number(timestamp)) > 5 * 60 * 1000)
    return res.status(403).json({ ok: false, error: "stale signature" });

  next();
}

app.get("/status", async (req, res) => {
  try {
    // Query the plugin API
    const response = await axios.get("http://127.0.0.1:27111/online", { timeout: 1500 });
    const players = response.data.players.map(p => p.name);
    const minePort = response.data.port || "24111"; // fallback

    return res.json({
      ok: true,
      port: minePort,
      players,
    });
  } catch (err) {
    // if failed to reach API
    console.error("Status check failed:", err.message);
    return res.json({ ok: false, message: "server offline" });
  }
});

// --- AUTHORIZE ENDPOINT (called by Auth Server) ---
app.post("/authorize", verifyAuth, async (req, res) => {
  const { ip, username } = req.body || {};
  if (!ip || !username)
    return res.status(400).json({ ok: false, error: "missing fields" });

  try {
    console.log(`Authorizing player ${username} (${ip})`);
    // 👇 Insert whitelist logic here
    // e.g., run command line or edit allowlist file:
    // await exec(`whitelist add ${username}`);
    // or use rcon, etc.

    return res.json({ ok: true, message: `Player ${username} authorized` });
  } catch (err) {
    console.error("Authorize failed:", err);
    res.status(500).json({ ok: false, error: "internal" });
  }
});

// TODO: create /logged api to check if authorized players logged after a 10 minute window

// --- LOCALHOST ONLY DEAUTHORIZE ---
function requireLocalhost(req, res, next) {
  const ip = req.ip || req.connection.remoteAddress;
  if (ip === "127.0.0.1" || ip === "::1") return next();
  res.status(403).json({ ok: false, error: "forbidden" });
}

app.post("/deauthorize", requireLocalhost, async (req, res) => {
  const { username } = req.body || {};
  if (!username) return res.status(400).json({ ok: false, error: "missing username" });

  try {
    console.log(`Deauthorizing player ${username}`);
    // 👇 Insert de-whitelist logic here
    // await exec(`whitelist remove ${username}`);

    return res.json({ ok: true, message: `Player ${username} deauthorized` });
  } catch (err) {
    console.error("Deauthorize failed:", err);
    res.status(500).json({ ok: false, error: "internal" });
  }
});

// --- SERVER LISTENER ---
app.listen(process.env.PORT, () => console.log("🧩 MineSupervisor mock listening on port 5001"));