package com.example.scaffold.platform

/** 转义 LIKE / ILIKE 中的通配符，让用户输入的 `%`、`_` 按字面匹配。SQL 中需配合 `ESCAPE '\'`。 */
fun String.escapeLike(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
