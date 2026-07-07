package top.example.security;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class LoginListener implements Listener {

    private final DomainSecurity plugin;
    private final Logger logger;
    
    private static boolean rateLimitEnabled = true;
    private static int timeWindowSeconds = 10;
    private static int maxFailures = 5;
    private static long blacklistDurationMillis = 300000;
    
    private static final Map<String, List<Long>> failureRecords = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, Long> blacklist = Collections.synchronizedMap(new HashMap<>());
    private static volatile int totalFailures = 0;

    public LoginListener() {
        this.plugin = DomainSecurity.getInstance();
        this.logger = plugin.getPluginLogger();
    }

    public static void updateRateLimitSettings() {
        DomainSecurity plugin = DomainSecurity.getInstance();
        if (plugin == null) {
            return;
        }
        rateLimitEnabled = plugin.isRateLimitEnabled();
        timeWindowSeconds = plugin.getTimeWindowSeconds();
        maxFailures = plugin.getMaxFailures();
        blacklistDurationMillis = plugin.getBlacklistDurationMillis();
    }

    public static Map<String, Long> getBlacklist() {
        cleanExpiredBlacklist();
        return Collections.unmodifiableMap(blacklist);
    }

    public static boolean removeFromBlacklist(String ip) {
        return blacklist.remove(ip) != null;
    }

    public static int getTotalFailures() {
        return totalFailures;
    }

    private static void cleanExpiredBlacklist() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    private static void cleanOldFailureRecords(String ip) {
        long windowStartTime = System.currentTimeMillis() - (long) timeWindowSeconds * 1000;
        List<Long> records = failureRecords.get(ip);
        if (records != null) {
            records.removeIf(timestamp -> timestamp < windowStartTime);
            if (records.isEmpty()) {
                failureRecords.remove(ip);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        String playerName = event.getName();
        String host = event.getHostname();
        String ipAddress = event.getAddress() != null ? event.getAddress().getHostAddress() : "未知";

        String displayHost = host;
        try {
            Object virtualHost = event.getClass().getMethod("getVirtualHost").invoke(event);
            if (virtualHost != null) {
                Object hostNameObj = virtualHost.getClass().getMethod("getHostName").invoke(virtualHost);
                if (hostNameObj != null) {
                    displayHost = hostNameObj.toString();
                }
            }
        } catch (Exception e) {
        }

        if (host == null) {
            host = "未知";
        }

        String cleanHost = extractHost(host);
        String cleanDisplayHost = extractHost(displayHost);
        
        logger.info(String.format("[DomainSecurity] 登录尝试 - 玩家: %s, 原始Host: %s, 显示Host: %s, 清理后Host: %s, IP: %s", playerName, host, displayHost, cleanHost, ipAddress));

        if (rateLimitEnabled) {
            if (isBlacklisted(ipAddress)) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§c§l连接被拒绝\n§7您的IP已被临时封禁\n§7请稍后再试");
                if (plugin.isLogBlacklisted()) {
                    logger.warning(String.format("[DomainSecurity] IP已拉黑 - 玩家: %s, IP: %s, Host: %s", playerName, ipAddress, cleanHost));
                }
                return;
            }
        }
        
        boolean isAllowed = checkDomain(playerName, cleanHost);
        
        if (!isAllowed) {
            if (rateLimitEnabled && !"未知".equals(ipAddress)) {
                recordFailure(ipAddress);
                if (shouldBlacklist(ipAddress)) {
                    addToBlacklist(ipAddress);
                    if (plugin.isLogBlacklisted()) {
                        logger.warning(String.format("[DomainSecurity] IP已拉黑 - 玩家: %s, IP: %s, Host: %s, 原因: 频率超限", playerName, ipAddress, cleanHost));
                    }
                }
            }
            
            String kickMessage = plugin.buildKickMessage(playerName, cleanDisplayHost);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, kickMessage);
            
            if (plugin.isLogBlocked()) {
                logger.warning(String.format("[DomainSecurity] 拦截登录 - 玩家: %s, IP: %s, Host: %s, 状态: 已拦截", playerName, ipAddress, cleanHost));
            }
        } else {
            if (rateLimitEnabled && !"未知".equals(ipAddress)) {
                failureRecords.remove(ipAddress);
            }
            
            if (plugin.isLogAllowed()) {
                logger.info(String.format("[DomainSecurity] 允许登录 - 玩家: %s, IP: %s, Host: %s, 状态: 已放行", playerName, ipAddress, cleanHost));
            }
        }
    }

    private boolean isBlacklisted(String ip) {
        cleanExpiredBlacklist();
        return blacklist.containsKey(ip);
    }

    private void recordFailure(String ip) {
        totalFailures++;
        failureRecords.computeIfAbsent(ip, k -> Collections.synchronizedList(new ArrayList<>()))
                     .add(System.currentTimeMillis());
        cleanOldFailureRecords(ip);
    }

    private boolean shouldBlacklist(String ip) {
        cleanOldFailureRecords(ip);
        List<Long> records = failureRecords.get(ip);
        return records != null && records.size() >= maxFailures;
    }

    private void addToBlacklist(String ip) {
        blacklist.put(ip, System.currentTimeMillis() + blacklistDurationMillis);
        failureRecords.remove(ip);
    }

    private String extractHost(String host) {
        if (host == null || host.isEmpty()) {
            return "未知";
        }

        int portIndex = host.indexOf(':');
        String result;
        if (portIndex > 0) {
            result = host.substring(0, portIndex);
        } else {
            result = host;
        }

        if (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        
        return result;
    }

    private boolean checkDomain(String playerName, String host) {
        if (host == null || host.isEmpty()) {
            logger.warning("[DomainSecurity] Host为空，拒绝连接");
            return false;
        }

        String trimmedHost = host.trim().toLowerCase();
        logger.info(String.format("[DomainSecurity] 域名校验 - Host: %s, 小写后: %s, 是否IP: %s", host, trimmedHost, isIpAddress(trimmedHost)));

        if (isIpAddress(trimmedHost)) {
            List<String> allowedPlayers = plugin.getAllowedPlayers();
            logger.info(String.format("[DomainSecurity] 是IP地址，检查白名单玩家: %s", allowedPlayers));
            for (String allowed : allowedPlayers) {
                if (playerName.equalsIgnoreCase(allowed.trim())) {
                    logger.info(String.format("[DomainSecurity] 玩家 %s 在白名单中，允许登录", playerName));
                    return true;
                }
            }
            logger.warning(String.format("[DomainSecurity] IP地址登录被拒绝，玩家 %s 不在白名单中", playerName));
            return false;
        }

        List<String> blockedDomains = plugin.getBlockedDomains();
        logger.info(String.format("[DomainSecurity] 检查黑名单域名: %s", blockedDomains));
        for (String blocked : blockedDomains) {
            if (trimmedHost.equalsIgnoreCase(blocked.trim())) {
                logger.warning(String.format("[DomainSecurity] 域名 %s 在黑名单中", trimmedHost));
                return false;
            }
        }

        List<String> allowedDomains = plugin.getAllowedDomains();
        logger.info(String.format("[DomainSecurity] 检查放行域名列表: %s", allowedDomains));
        for (String allowed : allowedDomains) {
            if (trimmedHost.equalsIgnoreCase(allowed.trim())) {
                logger.info(String.format("[DomainSecurity] 域名 %s 在放行列表中", trimmedHost));
                return true;
            }
        }

        logger.warning(String.format("[DomainSecurity] 域名 %s 不在放行列表中，拒绝登录", trimmedHost));
        return false;
    }

    private boolean isIpAddress(String host) {
        if (host == null || host.isEmpty()) {
            return true;
        }
        
        String temp = host;
        if (temp.startsWith("[")) {
            int endBracket = temp.indexOf(']');
            if (endBracket > 0) {
                temp = temp.substring(1, endBracket);
            }
        } else {
            int colonIndex = temp.lastIndexOf(':');
            if (colonIndex > 0 && colonIndex < temp.length() - 1) {
                try {
                    Integer.parseInt(temp.substring(colonIndex + 1));
                    temp = temp.substring(0, colonIndex);
                } catch (NumberFormatException e) {
                }
            }
        }
        
        if (temp.contains(".")) {
            String[] parts = temp.split("\\.");
            if (parts.length == 4) {
                for (String part : parts) {
                    try {
                        int value = Integer.parseInt(part);
                        if (value < 0 || value > 255) {
                            return false;
                        }
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                return true;
            }
        }
        
        if (temp.contains(":") && !temp.contains(".")) {
            return true;
        }
        
        return false;
    }
}
