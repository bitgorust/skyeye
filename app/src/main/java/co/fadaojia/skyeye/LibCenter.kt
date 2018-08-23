package co.fadaojia.skyeye

import android.content.Context
import com.interjoy.skface.SKFace

class LibCenter constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: LibCenter? = null
        fun getInstance(context: Context) =
                INSTANCE ?: synchronized(this) {
                    INSTANCE ?: LibCenter(context)
                }
    }

    val skFace: SKFace by lazy {
        SKFace(context.applicationContext)
    }
}
