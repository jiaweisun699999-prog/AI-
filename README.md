# RAGANDBIADATE（MVP）
## 项目运行示例
<img width="765" height="1221" alt="image" src="https://github.com/user-attachments/assets/2c6ba0cd-dc5d-43cb-bbe8-a0a28b7a648d" />
主要是导入研报进入分析系统，输入命令后解析到数据库，再靠ollama模型进行
在 **Windows + Docker Desktop** 下可一键跑通的研报 RAG 最小实现：BM25 +（可选）向量混合检索，问答返回**可溯源引用片段**。

## 项目里有什么

| 组件 | 作用 |
|------|------|
| **OpenSearch** | BM25 关键词检索 |
| **Postgres + pgvector** | 存 chunk 与向量 |
| **MinIO** | 对象存储（原文入湖） |
| **rag-service**（Spring Boot） | `ingest` / `embed` / `ask` 等 HTTP API，自带简单网页 |

默认通过 **本机 Ollama** 拉 embedding（`bge-m3`）与可选对话模型；`docker-compose.yml` 里已配置 `RAG_OLLAMA_URL=http://host.docker.internal:11434`，容器内服务访问宿主机 Ollama。

## 环境要求

- **Windows 10/11**，已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（WSL2 后端建议开启）
- **Git**（克隆仓库）
- **Ollama**（安装并常驻运行，用于 embedding；问答若走本地模型也依赖它）
- 磁盘与内存：OpenSearch + 镜像建议预留若干 GB；机器内存紧张时可适当调低 compose 里 JVM 等配置

## 从零上手（推荐顺序）

### 1. 克隆仓库

```bash
git clone <你的仓库 SSH 或 HTTPS 地址>
cd RAGANDBIADATE
```

首次启动后会在本机生成 `./.data/`（数据库与索引数据，**勿提交到 Git**；仓库已 `.gitignore` 忽略）。

### 2. 准备 Ollama 与模型

在**宿主机**（不是容器里）执行：

```powershell
ollama pull bge-m3
```

默认对话 / 深度模型见 `docker-compose.yml` 或 `rag-service/src/main/resources/application.yml`；若与本机 `ollama list` 不一致，可改环境变量 `RAG_OLLAMA_DEFAULT_MODEL` 等。

确认 Ollama 已监听本机（一般为 `http://127.0.0.1:11434`）。

### 3. 启动全部服务

在**项目根目录**执行：

```powershell
docker compose up -d --build
```

首次拉镜像较慢。依赖健康后：

- 服务健康检查：<http://localhost:8080/health>
- 简单 Web 问答页：<http://localhost:8080/>
- OpenSearch Dashboards：<http://localhost:5601>

查看容器状态：

```powershell
docker compose ps
```

### 4. 放入文档并导入

1. 将 **`.pdf` / `.html` / `.txt` / `.md`** 放入项目根目录下的 **`inbox/`**（与 `docker-compose` 挂载到容器内 `/inbox` 对应）。
2. 触发导入（以下任选一种）。

**方式 A：运行仓库脚本（PowerShell，推荐）**

```powershell
.\scripts\ingest.ps1
```

**方式 B：一行命令（PowerShell）**

注意：JSON 放在**单引号**内，避免转义问题。

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/ingest/run' -ContentType 'application/json' -Body '{"domain":"ashare","source":"local","folder":"/inbox","recursive":true}'
```

**方式 C：Git Bash / macOS / Linux**

```bash
curl -s -X POST "http://localhost:8080/ingest/run" \
  -H "Content-Type: application/json" \
  -d '{"domain":"ashare","source":"local","folder":"/inbox","recursive":true}'
```

成功时返回 JSON 中含 `ok: true` 以及 `filesIngested`、`chunksInserted` 等字段。

### 5.（推荐）写入向量并开启语义召回

本机 Ollama 已安装 **`bge-m3`** 且服务已起的前提下：

**脚本：**

```powershell
.\scripts\embed-backfill.ps1
```

**或 PowerShell：**

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/embed/backfill' -ContentType 'application/json' -Body '{"domain":"ashare","limit":200}'
```

`limit` 可按 chunk 数量调大；可多次执行直到无新 chunk 需要嵌入。完成后用「同义改写」提问，命中通常更稳。

### 6. 提问

- 浏览器打开：<http://localhost:8080/>
- 或使用脚本 / 命令行：

```powershell
.\scripts\test-ask.ps1
```

**PowerShell 自定义问题示例：**

```powershell
$body = '{"question":"半导体研报的核心结论和风险是什么？","domain":"ashare","mode":"fast"}'
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/ask' -ContentType 'application/json' -Body $body
```

可选字段：`topK`（整数）、`mode`（`fast` 或 `deep`），与 `AskRequest` 一致。

**Git Bash：**

```bash
curl -s -X POST "http://localhost:8080/ask" \
  -H "Content-Type: application/json" \
  -d '{"question":"半导体研报的核心结论和风险是什么？","domain":"ashare","mode":"fast"}'
```

## 推送到 GitHub 的简要说明

- 使用 **SSH** 时，远程地址形如：`git@github.com:<用户名>/<仓库>.git`；若已误加 HTTPS，可执行：  
  `git remote set-url origin git@github.com:<用户名>/<仓库>.git`
- 勿提交 `.data/` 与 `rag-service/target/`；他人克隆后执行 `docker compose up` 会在本机重新生成数据目录。

## 常见问题

| 现象 | 处理思路 |
|------|----------|
| `ingest` / `ask` 返回 **400** | 多为请求体 **JSON 格式错误**。在 PowerShell 里不要用「嵌套 `powershell -Command` + 复杂 `\"`」拼 JSON；改用上文 **单引号包裹整段 JSON** 或 **`.\scripts\ingest.ps1`**。 |
| embedding 失败 / 超时 | 确认宿主机 Ollama 已启动；Docker 内访问本机是否可用 `host.docker.internal`（Windows Docker Desktop 一般可用）。 |
| 端口被占用 | `8080`、`5432`、`9200`、`5601` 等若被占用，需停掉冲突程序或改 `docker-compose.yml` 端口映射。 |
| 导入后网页无命中 | 确认已 ingest；尝试先 **embed/backfill**；问题与 `domain`（默认 `ashare`）是否与导入一致。 |

## 配置入口

- 默认端口与依赖：根目录 **`docker-compose.yml`**
- Spring 与 RAG 相关配置：`rag-service/src/main/resources/application.yml`；容器环境覆盖见 **`application-docker.yml`** 及 compose 中的 `RAG_*` 环境变量。

## 后续规划（非当前 MVP 必选）

- 更强的 Agent 编排与结构化输出
- 评测与失败样本回流（调参 / 补数据 / 重切分 / 重嵌入）
- 与业务库 SQL 等的多路召回增强

---

如有问题，可先查 **`http://localhost:8080/health`** 与 `docker compose ps`，再结合上表排查。
