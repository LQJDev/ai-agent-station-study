# 📘 es-mcp 工具调用指南（Elasticsearch 专用）

## 🧭 概述

本文档描述 **es-mcp 工具集** 的使用方法。
该工具用于执行所有 Elasticsearch 相关操作，包括索引查询、映射获取、分片信息查看等。

---

## 强约束（必须遵守）

### 唯一执行路径

所有 Elasticsearch 操作 **必须通过 es-mcp 提供的 MCP 工具完成**。

### 严格禁止的行为

以下行为**严格禁止**：

* 直接调用 Elasticsearch HTTP API（如 `http://elasticsearch:9200/...`）
* 直接构造 `_search`、`_mapping`、`_cat` 等 REST 请求
* 绕过 MCP 工具直接访问 ES
* 将 Elasticsearch 当作可直接请求的服务，而不是通过工具调用

### 正确方式

所有请求必须转化为 **MCP 工具调用**，调用格式如下：

```json
{
  "name": "search",
  "arguments": {
    "index": "order_data",
    "query_body": {
      "query": {
        "match_all": {}
      }
    }
  }
}
```

---

## 🧠 工具选择决策规则（Agent 必须遵循）

当用户请求涉及 Elasticsearch 时，必须执行以下步骤：

1. 判断用户意图

   * 查询数据 → 使用 `search` 或 `esql`
   * 查看字段结构 → 使用 `get_mappings`
   * 查看分片信息 → 使用 `get_shards`
   * 查看索引列表 → 使用 `list_indices`

2. 选择对应的 MCP 工具

3. 按工具的 `inputSchema` 构造 `arguments`

4. 发起工具调用，禁止跳过工具直接访问 Elasticsearch

---

## 🛠️ 工具列表（全部属于 es-mcp）

### 1. get_mappings

#### 功能

获取指定 Elasticsearch 索引的字段映射信息。

#### 参数

| 参数名   | 类型     | 必填 | 描述         |
| ----- | ------ | -: | ---------- |
| index | string |  是 | 要查询映射的索引名称 |

#### 适用场景

* 查看字段类型是否符合预期
* 排查字段映射导致的查询异常
* 辅助编写 DSL 查询

#### 调用示例

```json
{
  "name": "get_mappings",
  "arguments": {
    "index": "user_behavior_logs"
  }
}
```

---

### 2. search

#### 功能

执行 Elasticsearch DSL 查询，适用于复杂过滤、分页、排序、字段裁剪等场景。

#### 参数

| 参数名        | 类型     | 必填 | 描述                             |
| ---------- | ------ | -: | ------------------------------ |
| index      | string |  是 | 要查询的索引名称                       |
| query_body | object |  是 | 完整的 Elasticsearch Query DSL 对象 |
| fields     | array  |  否 | 需要返回的字段列表                      |

#### 适用场景

* 多条件过滤查询
* 分页查询
* 排序查询
* 仅返回指定字段

#### 调用示例

```json
{
  "name": "search",
  "arguments": {
    "index": "order_data",
    "fields": ["order_id", "amount"],
    "query_body": {
      "query": {
        "match": {
          "status": "success"
        }
      },
      "size": 10
    }
  }
}
```

---

### 3. esql

#### 功能

执行 Elasticsearch ES|QL 查询，适合类 SQL 风格的结构化查询与聚合分析。

#### 参数

| 参数名   | 类型     | 必填 | 描述                   |         |
| ----- | ------ | -: | -------------------- | ------- |
| query | string |  是 | 完整的 Elasticsearch ES | QL 查询语句 |

#### 适用场景

* 类 SQL 的快速查询
* 聚合统计
* 分组分析
* 结构化结果查看

#### 调用示例

```json
{
  "name": "esql",
  "arguments": {
    "query": "FROM order_data | LIMIT 10"
  }
}
```

---

### 4. get_shards

#### 功能

获取所有索引或指定索引的分片信息。

#### 参数

| 参数名   | 类型     | 必填 | 描述                   |
| ----- | ------ | -: | -------------------- |
| index | string |  否 | 指定索引名称；不传则查询所有索引分片信息 |

#### 适用场景

* 排查分片未分配、状态异常
* 查看索引分片分布
* 核对分片、副本配置

#### 调用示例

查询所有索引分片信息：

```json
{
  "name": "get_shards",
  "arguments": {}
}
```

查询指定索引分片信息：

```json
{
  "name": "get_shards",
  "arguments": {
    "index": "product_catalog"
  }
}
```

---

### 5. list_indices

#### 功能

按索引匹配规则列出 Elasticsearch 中符合条件的索引。

#### 参数

| 参数名           | 类型     | 必填 | 描述               |
| ------------- | ------ | -: | ---------------- |
| index_pattern | string |  是 | 索引匹配规则，支持通配符 `*` |

#### 适用场景

* 查找某类索引
* 确认索引是否存在
* 为后续查询选择目标索引

#### 调用示例

列出所有以 `log_` 开头的索引：

```json
{
  "name": "list_indices",
  "arguments": {
    "index_pattern": "log_*"
  }
}
```

列出指定名称的索引：

```json
{
  "name": "list_indices",
  "arguments": {
    "index_pattern": "user_profile"
  }
}
```

---

## ❌ 错误示例（禁止）

以下写法都是错误的，因为它们绕过了 MCP 工具：

```http
GET /order_data/_search
```

```http
http://elasticsearch:9200/index/_mapping
```

```http
GET /_cat/indices?v
```

---

## ✅ 正确示例

```json
{
  "name": "search",
  "arguments": {
    "index": "order_data",
    "query_body": {
      "query": {
        "match_all": {}
      },
      "size": 10
    }
  }
}
```

---

## 🧩 使用建议（提升 Agent 稳定性）

* 复杂条件查询优先使用 `search`
* 类 SQL 风格查询优先使用 `esql`
* 不确定索引名称时先使用 `list_indices`
* 查询结果异常时优先使用 `get_mappings` 检查字段结构
* 排查索引健康或分片问题时使用 `get_shards`

---

## 📌 通用注意事项

1. 本文档中的全部工具均为只读操作，不会修改 Elasticsearch 数据。
2. 调用时必须严格匹配工具定义的参数类型。
3. 必填参数不可缺失。
4. 调用格式必须使用：

```json
{
  "name": "<tool_name>",
  "arguments": { ... }
}
```

5. 不允许使用如下格式：

```json
{
  "tool": "es-mcp",
  "name": "search",
  "parameters": { ... }
}
```

因为这不是当前 MCP 实际注册的调用结构。

---

## 📌 总结

| 概念            | 说明                             |
| ------------- | ------------------------------ |
| Elasticsearch | 查询和分析能力目标，不是直接调用入口             |
| es-mcp        | Elasticsearch 的唯一工具入口          |
| 正确调用格式        | `name + arguments`             |
| 错误调用方式        | 直接 HTTP / REST 请求，或使用错误的调用包装结构 |

---