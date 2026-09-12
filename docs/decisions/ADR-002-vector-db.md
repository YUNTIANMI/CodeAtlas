# ADR-002：选择 Qdrant 作为向量数据库

- **状态：** 已接受
- **日期：** 2026-09-12

## 背景

RAG 是本项目的核心能力，需要存储文档 Chunk 与代码 Chunk 的 Embedding 向量，并支持基于相似度的 Top-K 检索。

## 决策

采用 **Qdrant** 作为向量数据库。

## 理由

1. **独立部署简单**：单个 Docker 容器即可运行，适合个人项目。
2. **检索性能充足**：HNSW 索引，万级 Chunk 规模下毫秒级响应。
3. **过滤能力完善**：支持在向量检索时按 `project_id`、`source_type` 等 metadata 过滤，天然满足"项目数据隔离"需求。
4. **资源占用低**：相比需要 JVM 的向量方案，Qdrant（Rust 实现）内存占用更友好。
5. **提供官方 REST / gRPC 接口**：Java 客户端接入成本低。

## 备选方案

| 方案 | 未采用原因 |
|---|---|
| pgvector | 可复用 MySQL/PostgreSQL 减少组件，但向量检索性能与过滤能力弱于专用向量库 |
| Milvus | 功能最强、适合大规模，但部署依赖重（需 etcd、MinIO），个人项目过重 |
| Chroma | 轻量易用，但生产稳定性与权限能力较弱 |
| Elasticsearch | 全文检索强，但向量检索非其强项，且资源占用高 |

## 影响

- 形成双库结构：MySQL 存结构化数据，Qdrant 存向量，通过 metadata 中的 `project_id` / `source_id` 回指
- 删除文档或代码时必须同步删除对应向量，否则会出现"检索到已删除内容"
- 向量写入通过统一的 `VectorStoreService` 封装，避免业务代码与 Qdrant 强耦合
