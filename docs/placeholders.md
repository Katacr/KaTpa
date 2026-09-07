# PlaceholderAPI 占位符

安装 PlaceholderAPI 后，KaTpa 会自动注册 `katpa` 前缀的占位符，可用于任何支持 PAPI 的插件（如全息图、计分板、菜单）。

占位符按创建时间排序，越靠前越早创建（`%katpa_home_1%` 为最早设置的家）。

## 家（Home）

| 占位符 | 说明 |
| --- | --- |
| `%katpa_home_<n>%` | 第 n 个家的全部信息，逗号分隔：`服务器,世界,x,y,z,yaw,pitch` |
| `%katpa_home_<n>_location%` | 简略位置，逗号分隔：`世界,x,y,z`（不含服务器与朝向） |
| `%katpa_home_<n>_server%` | 所在服务器 ID |
| `%katpa_home_<n>_world%` | 所在世界 |
| `%katpa_home_<n>_x%` | X 坐标 |
| `%katpa_home_<n>_y%` | Y 坐标 |
| `%katpa_home_<n>_z%` | Z 坐标 |
| `%katpa_home_<n>_yaw%` | 朝向（偏航） |
| `%katpa_home_<n>_pitch%` | 俯仰角 |

## 地标（Warp）

| 占位符 | 说明 |
| --- | --- |
| `%katpa_warp_<n>%` | 第 n 个地标的全部信息，逗号分隔 |
| `%katpa_warp_<n>_location%` | 简略位置，逗号分隔：`世界,x,y,z`（不含服务器与朝向） |
| `%katpa_warp_<n>_server%` | 所在服务器 ID |
| `%katpa_warp_<n>_world%` | 所在世界 |
| `%katpa_warp_<n>_x%` | X 坐标 |
| `%katpa_warp_<n>_y%` | Y 坐标 |
| `%katpa_warp_<n>_z%` | Z 坐标 |
| `%katpa_warp_<n>_yaw%` | 朝向（偏航） |
| `%katpa_warp_<n>_pitch%` | 俯仰角 |

## 上次传送位置（Back）

记录玩家上次被传送前的位置（仅单条）。

| 占位符 | 说明 |
| --- | --- |
| `%katpa_back%` | 全部信息，逗号分隔：`服务器,世界,x,y,z,yaw,pitch` |
| `%katpa_back_location%` | 简略位置，逗号分隔：`世界,x,y,z`（不含服务器与朝向） |
| `%katpa_back_server%` | 所在服务器 ID |
| `%katpa_back_world%` | 所在世界 |
| `%katpa_back_x%` | X 坐标 |
| `%katpa_back_y%` | Y 坐标 |
| `%katpa_back_z%` | Z 坐标 |
| `%katpa_back_yaw%` | 朝向（偏航） |
| `%katpa_back_pitch%` | 俯仰角 |

## 死亡位置（Dback）

| 占位符 | 说明 |
| --- | --- |
| `%katpa_dback_<n>%` | 第 n 个死亡位置的完整信息，逗号分隔 |
| `%katpa_dback_<n>_location%` | 简略位置，逗号分隔：`世界,x,y,z`（不含服务器与朝向） |
| `%katpa_dback_<n>_server%` | 所在服务器 ID |
| `%katpa_dback_<n>_world%` | 所在世界 |
| `%katpa_dback_<n>_x%` | X 坐标 |
| `%katpa_dback_<n>_y%` | Y 坐标 |
| `%katpa_dback_<n>_z%` | Z 坐标 |
| `%katpa_dback_<n>_yaw%` | 朝向（偏航） |
| `%katpa_dback_<n>_pitch%` | 俯仰角 |

## 通用

| 占位符 | 说明 |
| --- | --- |
| `%katpa_mode%` | 当前玩家的请求接收方式，返回本地化名称：`对话框` / `聊天` / `双击潜行` |

> 索引 `<n>` 从 1 开始；超出实际数量时返回空字符串。坐标整数不显示小数部分。
