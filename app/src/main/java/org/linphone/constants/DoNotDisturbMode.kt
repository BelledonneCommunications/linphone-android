package org.linphone.constants

import androidx.annotation.IntDef

const val DND_ALWAYS_ON = 0
const val DND_SCHEDULED = 1

@IntDef(
    DND_ALWAYS_ON,
    DND_SCHEDULED
)
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY,
)
annotation class DoNotDisturbMode
