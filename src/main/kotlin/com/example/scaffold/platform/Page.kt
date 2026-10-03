package com.example.scaffold.platform

/** 分页查询结果。 */
data class Page<T>(val items: List<T>, val total: Long)
