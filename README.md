# DomainSecurity

Minecraft服务器域名安全校验插件，用于限制玩家只能通过指定域名连接服务器。

## 功能特性

- **域名白名单** - 只允许通过指定域名连接服务器
- **域名黑名单** - 禁止特定域名连接
- **玩家白名单** - 允许指定玩家通过IP地址直接连接
- **连接频率限制** - 防止暴力破解和扫描攻击
- **IP拉黑** - 高频失败自动拉黑IP地址
- **日志记录** - 记录所有连接尝试和拦截信息

## 安装方法

1. 将 `DomainSecurity-1.0.0.jar` 放入服务器的 `plugins` 目录
2. 启动服务器，插件会自动生成配置文件
3. 根据需要修改 `plugins/DomainSecurity/config.yml`

## 配置说明

### 域名配置

```yaml
domain:
  # 放行的域名列表（精确匹配）
  allowed-domains:
    - "mc.example.com"
    - "mc.example.net"
  
  # 禁止的域名列表（直接踢出）
  blocked-domains:
    - "mc1.example.com"
```

**重要：关于SRV解析**

如果您的域名配置了Minecraft SRV记录解析，需要注意以下事项：

- 插件校验的是服务器实际收到的连接地址（来自Minecraft握手包）
- 客户端输入的域名经过DNS SRV解析后，服务器收到的是解析后的地址
- 示例：若您设置了 `_minecraft._tcp.mc.example.com` SRV记录指向 `mc1.example.com`
  - 玩家输入 `mc.example.com` 连接时，服务器收到的是 `mc1.example.com`
  - 因此需要将 `mc1.example.com` 添加到 `allowed-domains` 中

### 玩家白名单

```yaml
whitelist:
  # 允许使用IP地址登录的玩家列表
  allowed-players:
    - "AdminPlayer"
    - "Developer"
```

白名单中的玩家可以直接通过IP地址连接服务器，不受域名限制。

### 连接频率限制

```yaml
rate-limit:
  # 是否启用频率限制
  enabled: true
  # 时间窗口（秒）
  time-window-seconds: 10
  # 最大失败次数
  max-failures: 5
  # 拉黑时长（分钟）
  blacklist-duration-minutes: 5
```

同一IP在10秒内校验失败5次将被自动拉黑5分钟。

## 命令

| 命令 | 描述 | 权限 |
|------|------|------|
| `/domainsecurity` | 查看帮助 | `domainsecurity.admin` |
| `/domainsecurity reload` | 重新加载配置 | `domainsecurity.admin` |
| `/domainsecurity export` | 导出拉黑IP日志 | `domainsecurity.admin` |
| `/domainsecurity blacklist` | 查看拉黑列表 | `domainsecurity.admin` |
| `/domainsecurity unban <IP>` | 解除指定IP拉黑 | `domainsecurity.admin` |

## 权限

| 权限 | 描述 | 默认 |
|------|------|------|
| `domainsecurity.admin` | 允许管理插件 | OP |

## 技术说明

- 基于 Paper API 1.21.1 开发
- 支持 Java 21+
- 兼容 Spigot/Paper 服务端

## 许可证

MIT License

## 构建

```bash
mvn package
```

生成的JAR文件位于 `target/DomainSecurity-1.0.0.jar`
