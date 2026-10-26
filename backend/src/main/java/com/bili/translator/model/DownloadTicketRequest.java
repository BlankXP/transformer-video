package com.bili.translator.model;

/**
 * 申请一次性下载票据的请求体。
 *
 * @param kind video / burned / srt / txt
 */
public record DownloadTicketRequest(String taskId, String kind) {
}
