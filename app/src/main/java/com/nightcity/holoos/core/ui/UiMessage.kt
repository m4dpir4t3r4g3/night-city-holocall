package com.nightcity.holoos.core.ui

import android.content.Context
import androidx.annotation.StringRes

/** A string resource + format args, resolved in the UI so ViewModels stay Context-free. */
data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(res, *args.toTypedArray())
}
