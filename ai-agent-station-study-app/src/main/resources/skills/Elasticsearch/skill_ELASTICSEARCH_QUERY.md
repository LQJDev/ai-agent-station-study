# 日志查询与集群排障指南
## 🛠 工具列表
### 1. list_indices
```json
{ "index_pattern": "string" }
```

### 2. get_mappings
```json
{ "index": "string" }
```

### 3. search（⚠️核心工具）
```json
{
  "index": "string",
  "query_body": {
    "size": 10,
    "query": { "bool": { "filter": [] } },
    "sort": [{ "@timestamp": "desc" }]
  },
  "fields": ["field1"]
}
```

### 4. esql
```json
{ "query": "FROM index | LIMIT 10" }
```

### 5. get_shards
```json
{ "index": "string" }
```

## 🧠 执行规则（必须遵守）
### 1. 渐进式查询流程
- 不确定目标索引 → 先执行 `list_indices`
- 已知索引名称 → 执行 `get_mappings` 确认字段
- 确认字段后 → 执行 `search` 进行数据查询
- ❌ 禁止跳步骤 / 猜测字段名称

### 2. query_body 编写规则（强制）
`query_body` 必须包含以下内容：
- `size`：默认值 10
- `sort`：固定格式 `[{ "@timestamp": "desc" }]`
- 时间范围：默认 `now-15m ~ now`

### 3. DSL 模板
#### 精确匹配
```json
{ "term": { "field.keyword": "value" } }
```

#### 时间范围
```json
{
  "range": {
    "@timestamp": {
      "gte": "now-15m",
      "lte": "now"
    }
  }
}
```

### 4. 返回数据控制规则
- 必须满足 `size ≤ 10`
- 必须通过 `fields` 指定返回字段

### 5. 集群排障规则
- 遇到 ES 异常 → 执行 `get_shards`
- 重点关注分片状态：`UNASSIGNED`

## 🚫 禁止操作
- 跳过 `get_mappings` 直接执行 `search`
- `query_body` 为空或格式为字符串
- 返回大量数据（size 超过 10）

## ✅ 标准示例
```json
{
  "index": "logs-*",
  "query_body": {
    "size": 10,
    "sort": [{ "@timestamp": "desc" }],
    "query": {
      "bool": {
        "filter": [
          { "term": { "level.keyword": "ERROR" } },
          {
            "range": {
              "@timestamp": {
                "gte": "now-15m",
                "lte": "now"
              }
            }
          }
        ]
      }
    }
  },
  "fields": ["message", "@timestamp"]
}
```

### 总结
1. ES 查询需遵循「查索引→查映射→查数据」的渐进式流程，禁止跳步骤操作；
2. `search` 查询的 `query_body` 必须包含 size、sort、时间范围，且返回数据量不超过10条；
3. 排障优先检查分片状态，重点关注 `UNASSIGNED` 状态的分片。