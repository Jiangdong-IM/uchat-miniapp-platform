package com.uchat.miniapp.platform.integration;

public record LocalUchatResponse<T>(boolean success, int code, String msg, T data) {
    public static <T> LocalUchatResponse<T> ok(T data) {
        return new LocalUchatResponse<>(true, 200, "success", data);
    }

    public static LocalUchatResponse<Void> error(int code, String message) {
        return new LocalUchatResponse<>(false, code, message, null);
    }
}
