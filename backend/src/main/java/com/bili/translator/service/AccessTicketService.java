package com.bili.translator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一次性下载票据。
 *
 * <p>浏览器用 {@code <a href>} 直链下载时无法携带 Authorization 头，把长期 JWT 拼进 URL
 * 又会落进访问日志。这里换成「先用登录态申请一张短时票据，再用票据换文件」：
 * 票据是 122 bit UUID，不可枚举、只活 120 秒、用过一次立即失效。
 *
 * <p>票据里存的是服务端路径与文件名，不下发到浏览器。
 */
@Service
public class AccessTicketService {

    private static final Logger log = LoggerFactory.getLogger(AccessTicketService.class);

    /** 票据有效期：只够浏览器发起一次下载 */
    private static final long TTL_MILLIS = 120_000L;

    /** 超过这个数量才触发惰性清理，避免每次申请都扫一遍 */
    private static final int PURGE_THRESHOLD = 64;

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public record Ticket(String path, String filename, String contentType, long expiresAt) {
    }

    /** 申请票据，返回不可枚举的票据 ID */
    public String issue(String path, String filename, String contentType) {
        purgeIfNeeded();
        String id = UUID.randomUUID().toString();
        tickets.put(id, new Ticket(path, filename, contentType, System.currentTimeMillis() + TTL_MILLIS));
        return id;
    }

    /**
     * 消费票据：取出即删除，保证「一个票据只能换一次文件」。
     *
     * @return null 表示票据不存在、已被使用或已过期
     */
    public Ticket consume(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Ticket ticket = tickets.remove(id);
        if (ticket == null) {
            return null;
        }
        if (ticket.expiresAt() < System.currentTimeMillis()) {
            log.debug("下载票据已过期: {}", id);
            return null;
        }
        return ticket;
    }

    public long ttlSeconds() {
        return TTL_MILLIS / 1000;
    }

    /** 惰性清理过期票据：只在申请时检查，不额外开线程 */
    private void purgeIfNeeded() {
        if (tickets.size() < PURGE_THRESHOLD) {
            return;
        }
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }
}
