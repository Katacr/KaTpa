# 个人设置

输入以下指令打开设置对话框：

```text
/tpasetting
```

主界面可以选择请求接收方式，并显示白名单和黑名单人数。设置会自动保存，重新登录后仍然有效。

## 接收方式

### 对话框

收到请求时打开原生对话框。存在多条请求时，每条请求都会单独列出，并提供同意和拒绝按钮。

### 聊天点击文本

在聊天栏显示 `[同意]` 和 `[拒绝]`。适合希望继续观察游戏画面的玩家。

### 双击潜行

在服主设置的间隔内连续按两次潜行键即可同意。收到多条请求时会改为打开待处理列表，避免接受错误的玩家。

也可以使用指令切换：

```text
/tpasetting mode dialog
/tpasetting mode chat
/tpasetting mode sneak
```

## 白名单

白名单玩家发来的请求会自动同意。点击设置界面的“管理白名单”可以查看成员、移除成员或从在线玩家中添加成员。

对应指令：

```text
/tpasetting whitelist
/tpasetting whitelist add <玩家>
/tpasetting whitelist remove <玩家>
```

## 黑名单

黑名单玩家发来的请求会自动拒绝。点击设置界面的“管理黑名单”可以查看和修改成员。

对应指令：

```text
/tpasetting blacklist
/tpasetting blacklist add <玩家>
/tpasetting blacklist remove <玩家>
```

玩家不能把自己加入名单。同一名玩家只能位于其中一个名单；加入新名单时会自动从另一名单移除。
