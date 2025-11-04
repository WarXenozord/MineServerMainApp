import express from "express";

const app = express();
app.use(express.json());

let isStarting = false;
let isRunning = false;
let bootStart = 0;

app.post("/start", (req, res) => {
  if (isRunning || isStarting)
    return res.json({ ok: false, message: "Already starting or running" });

  console.log("🟡 Supervisor: Starting fake Minecraft server...");
  isStarting = true;
  bootStart = Date.now();

  setTimeout(() => {
    isStarting = false;
    isRunning = true;
    console.log("🟢 Supervisor: Minecraft is now running!");
  }, 10000); // 10s fake boot

  res.json({ ok: true, message: "Boot initiated" });
});

app.get("/status", (req, res) => {
  if (isRunning) {
    return res.json({
      ok: true,
      ip: "127.0.0.1:25565",
      players: ["Steve", "Alex"],
    });
  }
  if (isStarting) {
    const elapsed = ((Date.now() - bootStart) / 1000).toFixed(1);
    return res.json({ ok: false, message: `booting (${elapsed}s)` });
  }
  res.json({ ok: false, message: "server offline" });
});

app.listen(5001, () =>
  console.log("🧩 MineSupervisor mock listening on port 5001")
);