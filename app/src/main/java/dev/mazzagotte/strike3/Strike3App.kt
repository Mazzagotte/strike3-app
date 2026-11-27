package dev.mazzagotte.strike3

import android.app.Application
import dev.mazzagotte.strike3.data.CardRepository

class Strike3App : Application() {
    lateinit var repo: CardRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = CardRepository(this)
        repo.backfillCardNumbers()
    }
}
