package com.tweakdeck.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    fun engine(c: Context): SharedPreferences = c.getSharedPreferences("engine", Context.MODE_PRIVATE)
    fun launcher(c: Context): SharedPreferences = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    fun misc(c: Context): SharedPreferences = c.getSharedPreferences("misc", Context.MODE_PRIVATE)
}
