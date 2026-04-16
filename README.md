# RAGANDBIADATE (MVP)

本项目在 Windows + Docker Desktop 下提供一个可跑通的 RAG MVP：

- OpenSearch（BM25 关键词检索）
- Postgres + pgvector（向量库，后续接 embedding）
- MinIO（对象存储：PDF/HTML 原文入湖）
- Java `rag-service`（Spring Boot）：`/ask` 返回“可溯源”的引用片段

## 1. 启动依赖与服务

在项目根目录执行：

```powershell
docker compose up -d --build
```

等待健康检查通过后，访问：

- `http://localhost:8080/health`
- OpenSearch Dashboards：`http://localhost:5601`

## 2. 体验问答（当前先做检索+引用）

```powershell
curl -X POST http://localhost:8080/ask `
  -H "Content-Type: application/json" `
  -d '{\"question\":\"半导体研报的核心结论和风险是什么？\",\"domain\":\"ashare\",\"mode\":\"fast\"}'
```

## 2.1 导入本地文件夹（最小 ingest 流程）

1) 把研报文件放进 `./inbox/`（宿主机目录）

2) 触发导入（默认读取容器内 `/inbox`）

```powershell
powershell -NoProfile -Command "(Invoke-WebRequest -Method Post -Uri 'http://localhost:8080/ingest/run' -ContentType 'application/json' -Body '{\"\"domain\"\":\"\"ashare\"\",\"\"source\"\":\"\"local\"\",\"\"folder\"\":\"\"/inbox\"\",\"\"recursive\"\":true}').Content"
```

导入后再去 `http://localhost:8080/` 提问，不同问题会命中不同引用片段。

## 2.2 生成向量（Ollama embedding）并开启语义召回

本项目使用 Ollama 的 embedding API，把 `chunks.embedding` 写入 pgvector，然后 `/ask` 会做混合召回（BM25 + 向量）。

1) 确保你本机 Ollama 已下载 embedding 模型（默认配置是 `bge-m3`）  
如果没有，请先执行（你在宿主机 PowerShell 里跑）：

```powershell
ollama pull bge-m3
```

2) 回填 embedding（把没有 embedding 的 chunks 批量向量化）

```powershell
powershell -NoProfile -Command "(Invoke-WebRequest -Method Post -Uri 'http://localhost:8080/embed/backfill' -ContentType 'application/json' -Body '{\"\"domain\"\":\"\"ashare\"\",\"\"limit\"\":200}').Content"
```

回填完成后，尝试用“同义改写”的问法提问，命中会更稳定。

## 3. 下一步（你要的“金融研报智能问答与分析 Agent”）

当前 MVP 已把“数据层 + 检索层 + Java API”打通，下一步会加：

- embedding 生成（本地部署开源 embedding 模型）
- 向量检索 + 多路召回（BM25 + vector + SQL）
- 本地大模型生成（把引用片段组织成结构化结论）
- 评测与回流（失败样本 → 调参/补数据/重切分/重嵌入）

