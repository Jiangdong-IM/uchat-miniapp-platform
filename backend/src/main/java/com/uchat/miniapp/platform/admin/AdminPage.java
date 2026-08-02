package com.uchat.miniapp.platform.admin;

import java.util.List;

public record AdminPage<T>(List<T> items, int page, int pageSize, long total) {
}
