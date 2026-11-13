package com.navidrome.tv.util

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** 非 Compose 层（数据层异常、ViewModel 错误消息）的本地化字符串解析。 */
object L10n {
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun str(@StringRes resId: Int, vararg args: Any?): String =
        appContext?.getString(resId, *args) ?: ""

    fun quantity(@PluralsRes resId: Int, count: Int, vararg args: Any?): String =
        appContext?.resources?.getQuantityString(resId, count, *args) ?: ""
}
