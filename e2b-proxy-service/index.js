import express from "express";
import cors from "cors";
import "dotenv/config";
import { Sandbox } from "@e2b/code-interpreter";

const app = express();
const PORT = process.env.PORT || 3001;

// 1. 基础中间件
app.use(cors());
app.use(express.json({ limit: "100mb" }));

// 打印 API Key 状态 (隐藏部分内容以示安全)
const apiKey = process.env.E2B_API_KEY;
if (apiKey) {
  console.log(`[Proxy] E2B API Key 已加载: ${apiKey.substring(0, 8)}...`);
} else {
  console.error("[Proxy] ❌ 警告: 未找到 E2B_API_KEY，请检查 .env 文件！");
}

// 2. 调试路由 (用于验证服务是否启动成功)
app.get("/", (req, res) => {
  res.send("✅ E2B Proxy Service is running!");
});

app.get("/health", (req, res) => {
  res.json({ status: "UP", apiKeyConfigured: !!process.env.E2B_API_KEY });
});

// 3. 部署前端项目的端点
app.post("/deploy-frontend", async (req, res) => {
  const { systemName, projectFiles } = req.body;

  if (!systemName || !projectFiles) {
    console.error("[Proxy] 错误: 缺少必要的部署参数");
    return res
      .status(400)
      .json({ success: false, error: "Missing systemName or projectFiles" });
  }

  console.log(
    `[Proxy] 🚀 收到部署请求: [${systemName}] | 文件总数: ${Object.keys(projectFiles).length}`,
  );

  let sbx;
  try {
    // 创建沙箱
    console.log("[Proxy] 正在创建沙箱...");
    sbx = await Sandbox.create({
      timeoutMs: 1800 * 1000, // 30分钟
      metadata: { project: systemName, source: "agentai-proxy" },
    });
    console.log(`[Proxy] 沙箱创建成功: ${sbx.sandboxId}`);

    // 写入前端文件
    const uploadPromises = [];
    let fileCount = 0;
    for (const [fullPath, content] of Object.entries(projectFiles)) {
      if (fullPath.startsWith("frontend/")) {
        const relativePath = fullPath.substring("frontend/".length);
        const remotePath = `/home/user/${relativePath}`;
        uploadPromises.push(sbx.files.write(remotePath, content));
        fileCount++;
      }
    }

    console.log(`[Proxy] 正在上传 ${fileCount} 个前端文件...`);
    await Promise.all(uploadPromises);
    console.log(`[Proxy] 文件上传完成`);

    // 先同步执行 npm install，等待完成
    console.log("[Proxy] 正在执行 npm install...");
    const installResult = await sbx.commands.run(
      "npm install --legacy-peer-deps 2>&1",
      { cwd: "/home/user", timeoutMs: 180000 }
    );
    if (installResult.exitCode !== 0) {
      throw new Error("npm install 失败: " + installResult.stdout.slice(-500));
    }
    console.log("[Proxy] npm install 完成，正在启动 dev server...");

    // 异步启动 vite（vite.config.js 已配置 host/port，无需重复传参）
    sbx.commands
      .run("npm run dev", {
        cwd: "/home/user",
        onStdout: (data) => console.log(`[Sandbox Log] ${data.trim()}`),
        onStderr: (data) => console.error(`[Sandbox Error] ${data.trim()}`),
      })
      .catch((err) => console.error("[Proxy] 沙箱进程中断:", err.message));

    // 等待 vite 启动（最多 90 秒，每 3 秒检查一次）
    let ready = false;
    for (let i = 0; i < 30; i++) {
      await new Promise((r) => setTimeout(r, 3000));
      try {
        const check = await sbx.commands.run(
          "curl -s -o /dev/null -w '%{http_code}' http://localhost:5173",
          { timeoutMs: 5000 }
        );
        const code = check.stdout?.trim();
        if (code && code !== "000" && code !== "") {
          console.log(`[Proxy] 端口 5173 就绪 (HTTP ${code})，耗时约 ${(i + 1) * 3}s`);
          ready = true;
          break;
        }
      } catch (_) { /* 继续等待 */ }
    }
    if (!ready) {
      console.warn("[Proxy] 等待超时（90s），vite 可能仍在启动中");
    }

    const previewUrl = `https://5173-${sbx.sandboxId}.e2b.dev`;
    console.log(`[Proxy] ✨ 部署成功! 预览地址: ${previewUrl}`);

    res.json({
      success: true,
      sandboxId: sbx.sandboxId,
      previewUrl: previewUrl,
      timeoutSeconds: 1800,
    });
  } catch (error) {
    console.error("[Proxy] ❌ 部署流程崩溃:", error.message);
    if (sbx) {
      console.log("[Proxy] 正在清理失败的沙箱...");
      await sbx.kill().catch(() => {});
    }
    res.status(500).json({
      success: false,
      error: error.message,
    });
  }
});

// 4. 关闭沙箱
app.post("/kill-sandbox", async (req, res) => {
  const { sandboxId } = req.body;
  if (!sandboxId) return res.status(400).json({ success: false, error: "Missing sandboxId" });
  try {
    const sbx = await Sandbox.connect(sandboxId);
    await sbx.kill();
    console.log(`[Proxy] 沙箱已关闭: ${sandboxId}`);
    res.json({ success: true });
  } catch (error) {
    console.error("[Proxy] 关闭沙箱失败:", error.message);
    res.status(500).json({ success: false, error: error.message });
  }
});

// 5. 启动监听
app.listen(PORT, "0.0.0.0", () => {
  console.log("");
  console.log("================================================");
  console.log(`✅ E2B 代理服务已就绪`);
  console.log(`📍 本地地址: http://localhost:${PORT}`);
  console.log(`🔗 部署接口: http://localhost:${PORT}/deploy-frontend`);
  console.log(`💓 健康检查: http://localhost:${PORT}/health`);
  console.log("================================================");
  console.log("");
});
