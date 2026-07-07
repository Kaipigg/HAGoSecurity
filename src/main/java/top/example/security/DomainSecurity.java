package top.example.security;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class DomainSecurity extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static DomainSecurity instance;
    private Logger logger;
    
    private List<String> allowedDomains;
    private List<String> blockedDomains;
    private List<String> allowedPlayers;
    private boolean kickEnabled;
    private String kickTitle;
    private String separator;
    private String reason;
    private List<String> description;
    private String footerSeparator;
    private boolean logBlocked;
    private boolean logAllowed;
    private boolean logBlacklisted;
    
    private boolean rateLimitEnabled;
    private int timeWindowSeconds;
    private int maxFailures;
    private long blacklistDurationMillis;

    @Override
    public void onEnable() {
        instance = this;
        logger = getLogger();
        
        saveDefaultConfig();
        reloadConfig();
        
        getServer().getPluginManager().registerEvents(new LoginListener(), this);
        
        getCommand("domainsecurity").setExecutor(this);
        getCommand("domainsecurity").setTabCompleter(this);
        
        logger.info("§aDomainSecurity 插件已启用");
        logger.info("§a域名安全校验功能已激活");
    }

    @Override
    public void onDisable() {
        logger.info("§cDomainSecurity 插件已禁用");
        instance = null;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        
        allowedDomains = getConfig().getStringList("domain.allowed-domains");
        if (allowedDomains == null) {
            allowedDomains = new ArrayList<>();
        }
        blockedDomains = getConfig().getStringList("domain.blocked-domains");
        if (blockedDomains == null) {
            blockedDomains = new ArrayList<>();
        }
        
        allowedPlayers = getConfig().getStringList("whitelist.allowed-players");
        if (allowedPlayers == null) {
            allowedPlayers = new ArrayList<>();
        }
        
        kickEnabled = getConfig().getBoolean("kick-message.enabled", true);
        kickTitle = getConfig().getString("kick-message.title", "§c§lServer 安全拦截");
        separator = getConfig().getString("kick-message.separator", "---------------------------------------");
        reason = getConfig().getString("kick-message.reason", "§c✘ 连接地址无效");
        description = getConfig().getStringList("kick-message.description");
        footerSeparator = getConfig().getString("kick-message.footer-separator", "---------------------------------------");
        
        logBlocked = getConfig().getBoolean("logging.log-blocked", true);
        logAllowed = getConfig().getBoolean("logging.log-allowed", true);
        logBlacklisted = getConfig().getBoolean("logging.log-blacklisted", true);
        
        rateLimitEnabled = getConfig().getBoolean("rate-limit.enabled", true);
        timeWindowSeconds = getConfig().getInt("rate-limit.time-window-seconds", 10);
        maxFailures = getConfig().getInt("rate-limit.max-failures", 5);
        blacklistDurationMillis = (long) getConfig().getInt("rate-limit.blacklist-duration-minutes", 5) * 60 * 1000;
        
        LoginListener.updateRateLimitSettings();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("domainsecurity.admin")) {
            sender.sendMessage("§c你没有权限执行此命令");
            return true;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§aDomainSecurity 插件 v1.0.0");
            sender.sendMessage("§7可用命令:");
            sender.sendMessage("§e/domainsecurity reload §7- 重新加载配置");
            sender.sendMessage("§e/domainsecurity export §7- 导出拉黑IP日志");
            sender.sendMessage("§e/domainsecurity blacklist §7- 查看拉黑列表");
            sender.sendMessage("§e/domainsecurity unban <IP> §7- 解除指定IP拉黑");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("§aDomainSecurity 配置已重新加载");
            logger.info("[DomainSecurity] 配置已由 " + sender.getName() + " 重新加载");
            return true;
        }
        
        if (args[0].equalsIgnoreCase("export")) {
            exportBlacklistLog(sender);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("blacklist")) {
            showBlacklist(sender);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("unban")) {
            if (args.length < 2) {
                sender.sendMessage("§c用法: /domainsecurity unban <IP>");
                return true;
            }
            unbanIp(sender, args[1]);
            return true;
        }
        
        sender.sendMessage("§c未知命令，使用 /domainsecurity 查看帮助");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("domainsecurity.admin")) {
            return new ArrayList<>();
        }
        
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("reload");
            completions.add("export");
            completions.add("blacklist");
            completions.add("unban");
            return completions;
        }
        
        return new ArrayList<>();
    }

    public static DomainSecurity getInstance() {
        return instance;
    }

    public Logger getPluginLogger() {
        return logger;
    }

    public List<String> getAllowedDomains() {
        return allowedDomains;
    }

    public List<String> getBlockedDomains() {
        return blockedDomains;
    }

    public List<String> getAllowedPlayers() {
        return allowedPlayers;
    }

    public boolean isKickEnabled() {
        return kickEnabled;
    }

    public String getKickTitle() {
        return kickTitle;
    }

    public String getSeparator() {
        return separator;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getKickDescription() {
        return description;
    }

    public String getFooterSeparator() {
        return footerSeparator;
    }

    public boolean isLogBlocked() {
        return logBlocked;
    }

    public boolean isLogAllowed() {
        return logAllowed;
    }

    public boolean isLogBlacklisted() {
        return logBlacklisted;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public int getTimeWindowSeconds() {
        return timeWindowSeconds;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public long getBlacklistDurationMillis() {
        return blacklistDurationMillis;
    }

    private void exportBlacklistLog(CommandSender sender) {
        File logFile = new File(getDataFolder(), "blacklist_export_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".txt");
        
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write("DomainSecurity 拉黑IP日志导出\n");
            writer.write("导出时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n");
            writer.write("========================================\n\n");
            
            Map<String, Long> blacklist = LoginListener.getBlacklist();
            if (blacklist.isEmpty()) {
                writer.write("当前没有被拉黑的IP\n");
            } else {
                for (Map.Entry<String, Long> entry : blacklist.entrySet()) {
                    writer.write("IP: " + entry.getKey() + "\n");
                    writer.write("  拉黑到期时间: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(entry.getValue())) + "\n\n");
                }
            }
            
            writer.write("\n统计信息:\n");
            writer.write("  已拉黑IP数量: " + blacklist.size() + "\n");
            writer.write("  校验失败总次数: " + LoginListener.getTotalFailures() + "\n");
            
            sender.sendMessage("§a拉黑IP日志已导出到: " + logFile.getName());
            logger.info("[DomainSecurity] 拉黑IP日志已导出到: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            sender.sendMessage("§c导出日志失败: " + e.getMessage());
            logger.severe("[DomainSecurity] 导出日志失败: " + e.getMessage());
        }
    }

    private void showBlacklist(CommandSender sender) {
        Map<String, Long> blacklist = LoginListener.getBlacklist();
        
        if (blacklist.isEmpty()) {
            sender.sendMessage("§a当前没有被拉黑的IP");
            return;
        }
        
        sender.sendMessage("§e===== 拉黑IP列表 =====");
        int count = 1;
        for (Map.Entry<String, Long> entry : blacklist.entrySet()) {
            long remainingSeconds = (entry.getValue() - System.currentTimeMillis()) / 1000;
            String remainingStr;
            if (remainingSeconds <= 0) {
                remainingStr = "即将到期";
            } else if (remainingSeconds < 60) {
                remainingStr = remainingSeconds + "秒";
            } else {
                remainingStr = (remainingSeconds / 60) + "分钟";
            }
            sender.sendMessage("§7" + count + ". §c" + entry.getKey() + " §7(剩余: " + remainingStr + ")");
            count++;
        }
        sender.sendMessage("§e======================");
        sender.sendMessage("§7总计: " + blacklist.size() + " 个IP");
    }

    private void unbanIp(CommandSender sender, String ip) {
        boolean removed = LoginListener.removeFromBlacklist(ip);
        if (removed) {
            sender.sendMessage("§aIP " + ip + " 已解除拉黑");
            logger.info("[DomainSecurity] IP " + ip + " 已由 " + sender.getName() + " 解除拉黑");
        } else {
            sender.sendMessage("§cIP " + ip + " 不在拉黑列表中");
        }
    }

    public String buildKickMessage(String playerName, String host) {
        StringBuilder message = new StringBuilder();
        
        message.append(kickTitle).append("\n");
        message.append(separator).append("\n");
        message.append(reason).append("\n");
        
        for (String line : description) {
            String replacedLine = line
                    .replace("%player%", playerName)
                    .replace("%host%", host);
            message.append(replacedLine).append("\n");
        }
        
        message.append(footerSeparator);
        
        return message.toString();
    }
}
