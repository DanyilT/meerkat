package com.danyt.meerkat.app

import android.app.Application

/** Application class. Owns the [AppContainer] for the lifetime of the process. */
class MeerkatApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
