package com.apenkoder.niasfood.data.demo

import com.apenkoder.niasfood.di.AppContainer

/** Release builds contain no demo data and no seeded PINs. The real seeder lives in src/debug only. */
object DemoSeeder {
  @Suppress("UNUSED_PARAMETER")
  suspend fun seedIfNeeded(container: AppContainer): Boolean = false
}
