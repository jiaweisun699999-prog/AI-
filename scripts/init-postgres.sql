CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS chunks (
  id UUID PRIMARY KEY,
  domain TEXT NOT NULL,
  source TEXT NOT NULL,
  title TEXT,
  url TEXT,
  publish_time TIMESTAMPTZ,
  content TEXT NOT NULL,
  -- Default dimension for bge-m3 is 1024; adjust later if you choose another embedding model.
  embedding vector(1024),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chunks_publish_time ON chunks (publish_time DESC);
CREATE INDEX IF NOT EXISTS idx_chunks_domain ON chunks (domain);
CREATE INDEX IF NOT EXISTS idx_chunks_source ON chunks (source);

-- Demo seed data (so /ask works immediately with keyword retrieval + citations)
INSERT INTO chunks (id, domain, source, title, url, publish_time, content, metadata)
VALUES
  ('11111111-1111-1111-1111-111111111111', 'ashare', 'demo', '示例研报：半导体行业景气度', NULL, '2026-04-01T00:00:00Z',
   '结论：半导体设备国产替代加速。风险：需求不及预期、价格竞争加剧。关注：龙头公司订单与毛利率。',
   '{"stock_codes":["688981","002371"],"industry":"半导体"}'),
  ('22222222-2222-2222-2222-222222222222', 'ashare', 'demo', '示例研报：铜价与宏观', NULL, '2026-03-15T00:00:00Z',
   '结论：铜价受宏观流动性与供给扰动影响。建议：关注库存、冶炼产能与美元指数变化。',
   '{"commodity":["铜"],"industry":"有色金属"}')
ON CONFLICT (id) DO NOTHING;

